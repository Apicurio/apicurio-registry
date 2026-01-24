-- Initialize the orders database with tables for CDC demonstration
-- This script runs automatically when the postgres-orders container starts

-- Create orders table
CREATE TABLE IF NOT EXISTS orders (
    id SERIAL PRIMARY KEY,
    customer_name VARCHAR(255) NOT NULL,
    product VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    trace_id VARCHAR(64),  -- Store trace ID for correlation (human readable)
    tracingspancontext TEXT,  -- Serialized OpenTelemetry context for Debezium trace propagation
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_trace_id ON orders(trace_id);

-- Create a function to update the updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger to auto-update updated_at
DROP TRIGGER IF EXISTS update_orders_updated_at ON orders;
CREATE TRIGGER update_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Insert some sample data
INSERT INTO orders (customer_name, product, quantity, price, status) VALUES
    ('Alice Smith', 'Laptop', 1, 1299.99, 'PENDING'),
    ('Bob Johnson', 'Keyboard', 2, 79.99, 'CONFIRMED'),
    ('Carol Williams', 'Monitor', 1, 399.99, 'SHIPPED');

-- Grant permissions for replication (required by Debezium)
ALTER TABLE orders REPLICA IDENTITY FULL;

-- Display confirmation
DO $$
BEGIN
    RAISE NOTICE 'Orders database initialized successfully with % sample orders',
        (SELECT COUNT(*) FROM orders);
END $$;
