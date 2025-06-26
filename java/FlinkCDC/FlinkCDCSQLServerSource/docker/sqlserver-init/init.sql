-- Create SampleDataPlatform database
CREATE DATABASE SampleDataPlatform;
GO

-- Use the SampleDataPlatform database
USE SampleDataPlatform;
GO

-- Create login for flink_cdc
CREATE LOGIN flink_cdc WITH PASSWORD = 'FlinkCDC@123';
GO

-- Create user in SampleDataPlatform database
CREATE USER flink_cdc FOR LOGIN flink_cdc;
GO

-- Grant necessary permissions for CDC operations
ALTER ROLE db_owner ADD MEMBER flink_cdc;
GO

-- Enable CDC on the SampleDataPlatform database
USE SampleDataPlatform;
EXEC sys.sp_cdc_enable_db;
GO

-- Create Customers table with the specified schema
CREATE TABLE [dbo].[Customers]
(
    [CustomerID]    [int] IDENTITY (1,1) NOT NULL,
    [FirstName]     [nvarchar](40)       NOT NULL,
    [MiddleInitial] [nvarchar](40)       NULL,
    [LastName]      [nvarchar](40)       NOT NULL,
    [mail]          [varchar](50)        NULL,
    CONSTRAINT [CustomerPK] PRIMARY KEY CLUSTERED ([CustomerID] ASC)
        WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY];
GO

-- Enable CDC on the Customers table
EXEC sys.sp_cdc_enable_table
     @source_schema = N'dbo',
     @source_name = N'Customers',
     @role_name = NULL,
     @supports_net_changes = 0;
GO

-- Insert some sample data
INSERT INTO [dbo].[Customers] ([FirstName], [MiddleInitial], [LastName], [mail])
VALUES ('John', 'A', 'Doe', 'john.doe@example.com'),
       ('Jane', NULL, 'Smith', 'jane.smith@example.com'),
       ('Bob', 'R', 'Johnson', 'bob.johnson@example.com'),
       ('Alice', 'M', 'Williams', 'alice.williams@example.com'),
       ('Charlie', NULL, 'Brown', 'charlie.brown@example.com');
GO

PRINT 'Database initialization completed successfully!';
