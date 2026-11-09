How to create the budget burn graph for the current month:

Root formula is

```
USED_ERROR_BUDGET_PERCENTAGE_CURRENT_MONTH = (1 - (SUM_OF_ERROR_RATE_CURRENT_MONTH / MAX_SUM_OF_ERROR_RATE_CURRENT_MONTH)) * 100
```

where

```
SUM_OF_ERROR_RATE_CURRENT_MONTH = sum_over_time((ERROR_RATE_OR_ZERO * (1 for days in the current month else 0))[32d:1h]) # with 1 hour resolution
                                = sum_over_time((ERROR_RATE_OR_ZERO * on() group_left() (month() == bool vector(${__to:date:M})))[32d:1h])
```

```
ERROR_RATE_OR_ZERO = FAILED_REQUESTS / TOTAL_REQUESTS # with handling of missing data
```

```
MAX_SUM_OF_ERROR_RATE_CURRENT_MONTH = (1 - SLO) * (number of hours in the current month) // again working with 1 hour resolution
                                    = (1 - SLO) * on() group_left() (days_in_month() * 24)
```

```
SLO = 0.99
```

Resulting in the following query:

```
ERROR_RATE_OR_ZERO = (1 - (avg_over_time(up{job="apicurio-registry"}[$__range]) or vector(1))) + ((avg_over_time(up{job="apicurio-registry"}[$__range]) or vector(1)) * ((sum(rate(rest_requests_seconds_count{job="apicurio-registry", status_code_group="5xx"}[$__range])) / sum(rate(rest_requests_seconds_count{job="apicurio-registry"}[$__range]))) or vector(0)))
# See the alert rules ^
```

```
SUM_OF_ERROR_RATE_CURRENT_MONTH = sum_over_time((((1 - (avg_over_time(up{job="apicurio-registry"}[$__range]) or vector(1))) + ((avg_over_time(up{job="apicurio-registry"}[$__range]) or vector(1)) * ((sum(rate(rest_requests_seconds_count{job="apicurio-registry", status_code_group="5xx"}[$__range])) / sum(rate(rest_requests_seconds_count{job="apicurio-registry"}[$__range]))) or vector(0)))) * on() group_left() (month() == bool vector(${__to:date:M})))[32d:1h])
```

```
USED_ERROR_BUDGET_PERCENTAGE_CURRENT_MONTH = (1 - (sum_over_time((((1 - (avg_over_time(up{job="apicurio-registry"}[$__range]) or vector(1))) + ((avg_over_time(up{job="apicurio-registry"}[$__range]) or vector(1)) * ((sum(rate(rest_requests_seconds_count{job="apicurio-registry", status_code_group="5xx"}[$__range])) / sum(rate(rest_requests_seconds_count{job="apicurio-registry"}[$__range]))) or vector(0)))) * on() group_left() (month() == bool vector(${__to:date:M})))[32d:1h]) / ((1 - 0.99) * on() group_left() (days_in_month() * 24)))) * 100
```
