
-- Load dictionary
load data local infile 'C:/Users/Jojo le Barjos/Projects/crosswords/db/index'
into table Words
fields terminated by ',' 
enclosed by ''
lines terminated by '\n';


-- Load adjacency matrix
SET FOREIGN_KEY_CHECKS=0;
load data local infile 'C:/Users/Jojo le Barjos/Projects/crosswords/db/adjacency'
into table Neighbors
fields terminated by ',' 
enclosed by ''
lines terminated by '\n';
SET FOREIGN_KEY_CHECKS=1;

