-- this is the initial database creation script
-- this script should be run ONCE before running the application

-- create db if it doesn't exist --- 
CREATE DATABASE IF NOT EXISTS checkmate_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;



-- use the db
USE checkmate_db;




-- Create admin user for the application (optional, for development)
-- thuis is just for  local development


SELECT 'Database checkmate_db created successfully!' AS message;