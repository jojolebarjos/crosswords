
-- Load dictionary
load data local infile 'C:/Users/Jojo le Barjos/Projects/crosswords/db/index2word.csv'
into table Words
fields terminated by ',' 
enclosed by ''
lines terminated by '\r\n'
ignore 1 rows;


-- Load adjacency matrix
load data local infile 'C:/Users/Jojo le Barjos/Projects/crosswords/db/adjacency.csv'
into table Neighbors
fields terminated by ',' 
enclosed by ''
lines terminated by '\r\n'
ignore 1 rows;

