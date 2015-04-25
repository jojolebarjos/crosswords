
-- Drop data
delete from Items;
delete from Crosswords;
delete from Neighbors;
delete from Words;

-- Add some words
insert into Words values
	(1, 'A'),
	(2, 'B'),
	(3, 'C'),
	(4, 'D'),
	(5, 'E'),
	(6, 'F');

-- Create graph
insert into Neighbors values
	(1, 2, 0.5),
	(1, 3, 0.2),
	(2, 1, 0.5),
	(3, 1, 0.2),
	(3, 4, 0.4),
	(3, 5, 0.9),
	(4, 3, 0.4),
	(5, 3, 0.9),
	(5, 6, 0.9),
	(6, 5, 0.9);

-- Find words associated to 'A' and 'E', sorted by likelihood.
select word, score
from (
	select widfrom, sum(weight) as score
	from (
		select wid
		from Words
		where word in ('A', 'E')
	) Inputs
	inner join Neighbors on wid = widto
	group by widfrom
	order by score desc
) Outputs
inner join Words on wid = widfrom

