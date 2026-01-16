# Search Performance Benchmark

This example provides a performance benchmark comparing search functionality between:

- **Apicurio Registry 3.1.6** (baseline - before search optimizations)
- **Apicurio Registry 3.1.7-SNAPSHOT** (optimized - with issue #7010 improvements)

## Overview

Issue #7010 introduced several search query optimizations:

1. **PostgreSQL trigram indexes** - GIN indexes with pg_trgm for faster substring searches
2. **Window functions for count** - Single query returns both results and total count
3. **JOIN-based label filtering** - Replaces correlated EXISTS subqueries with efficient JOINs
4. **Composite indexes** - Optimized indexes for label table JOIN conditions

This benchmark measures the performance improvement across various search scenarios.

## Prerequisites

- Docker and Docker Compose
- curl
- jq
- Python 3
- Maven (optional, for building from source)

## Quick Start

### Option 1: Using pre-built images

If you have the optimized registry image already built:

```bash
# Make scripts executable
chmod +x *.sh

# Run the full benchmark (skip building)
./run-all.sh --skip-build
```

### Option 2: Build and run

To build the optimized registry from source and run benchmarks:

```bash
# Make scripts executable
chmod +x *.sh

# Run the full benchmark (includes building)
./run-all.sh
```

### Custom configuration

```bash
# Run with custom parameters
./run-all.sh --num-artifacts 5000 --iterations 100 --parallel 20

# Available options:
#   --skip-build       Skip building the registry image
#   --num-artifacts N  Number of test artifacts to create (default: 10000)
#   --iterations N     Number of benchmark iterations per test (default: 50)
#   --parallel N       Number of parallel seeding jobs (default: 10)
```

**Note:** The default of 10,000 artifacts meets the acceptance criteria for testing with large datasets (10k+ artifacts). For quicker test runs, use `--num-artifacts 1000`.

## Manual Steps

If you prefer to run each step manually:

### 1. Start the registries

```bash
docker compose up -d
```

This starts:
- `registry-baseline` on port 8080 (Apicurio Registry 3.1.6)
- `registry-optimized` on port 8081 (Current version with optimizations)

### 2. Seed test data

```bash
# Seed baseline registry (10k artifacts with 10 parallel jobs)
./seed-data.sh http://localhost:8080 10000 5 10 3 10

# Seed optimized registry
./seed-data.sh http://localhost:8081 10000 5 10 3 10
```

Parameters: `<registry-url> <num-artifacts> <labels-per-artifact> <num-groups> <num-versions> <parallel-jobs>`

### 3. Run benchmarks

```bash
./run-benchmark.sh 50
```

Parameter: number of iterations per test

### 4. View results

Results are saved to:
- `benchmark-results-YYYYMMDD-HHMMSS.csv` - Raw data
- `benchmark-summary-YYYYMMDD-HHMMSS.txt` - Summary statistics

### 5. Cleanup

```bash
# Stop containers
docker compose down

# Stop and remove data volumes
docker compose down -v
```

## Benchmark Tests

The benchmark runs the following search scenarios:

| Test | Description | Optimization Used |
|------|-------------|-------------------|
| `name_search_substring` | Search by name with wildcards (`*service*`) | Trigram indexes |
| `name_search_prefix` | Search by name prefix (`orders*`) | B-tree indexes |
| `description_search` | Search by description substring | Trigram indexes |
| `single_label_filter` | Filter by one label (key=value) | JOIN optimization |
| `label_key_only` | Filter by label key existence | JOIN optimization |
| `multi_label_filter` | Filter by two labels (AND) | Multi-JOIN optimization |
| `three_label_filter` | Filter by three labels (AND) | Multi-JOIN optimization |
| `name_and_label_search` | Combined name + label filter | Combined optimizations |
| `group_and_label_search` | Group filter with label | JOIN optimization |
| `large_result_set` | Large pagination (1000 results) | Window function count |
| `version_search_with_label` | Version search | All optimizations |

## Expected Results

With the optimizations enabled, you should see:

- **Label filter searches**: 20-50% improvement (JOIN vs EXISTS subquery)
- **Multi-label searches**: 30-60% improvement (multiple JOINs are more efficient)
- **Substring searches on PostgreSQL**: 10-30% improvement (trigram indexes)
- **Large result sets**: 15-25% improvement (window function avoids second COUNT query)

Actual results depend on data volume, hardware, and database configuration.

## Configuration Properties

The optimized registry uses these configuration properties:

```properties
# Enable JOIN-based label filtering (default: false)
APICURIO_STORAGE_SQL_SEARCH_USE_JOIN_FOR_LABELS=true

# Enable PostgreSQL ILIKE for case-insensitive search (default: false)
APICURIO_SEARCH_TRIGRAM_ENABLED=true
```

## Troubleshooting

### Registries not starting

Check Docker logs:
```bash
docker compose logs registry-baseline
docker compose logs registry-optimized
```

### Benchmark shows no improvement

1. Ensure both registries have the same amount of test data
2. Verify the optimized registry has the configuration flags enabled
3. Check that PostgreSQL is being used (not H2)

### Out of memory

Reduce the number of artifacts:
```bash
./run-all.sh --num-artifacts 1000
```

### Seeding is slow

Increase parallel jobs (default 10):
```bash
./run-all.sh --parallel 20
```

## Files

- `docker-compose.yml` - Docker Compose configuration for both registries
- `seed-data.sh` - Script to populate registries with test data
- `run-benchmark.sh` - Benchmark runner script
- `run-all.sh` - Complete workflow script
- `README.md` - This file

## Related

- [Issue #7010](https://github.com/Apicurio/apicurio-registry/issues/7010) - Search Improvements Phase 1
- [PR #7096](https://github.com/Apicurio/apicurio-registry/pull/7096) - Implementation PR
