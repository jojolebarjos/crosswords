
-- Dictionary
create table Words (
	wid int not null,
	word varchar(200) not null,
	primary key (wid),
	unique index (word)
);

-- Adjacency matrix
create table Neighbors (
	widfrom int not null,
	widto int not null,
	weight float not null,
	primary key (widfrom, widto),
	foreign key (widfrom)
		references Words(wid)
		on update cascade
		on delete cascade,
	foreign key (widto)
		references Words(wid)
		on update cascade
		on delete cascade
);

-- Crosswords infos
create table Crosswords (
	cwid int not null,
	source varchar(50),
	lang varchar(5),
	title varchar(50),
	url varchar(100),
	author varchar(50),
	cwdate date,
	difficulty int,
	primary key (cwid)
);

-- Crosswords items
create table Items (
	cwid int not null,
	wid int not null,
	xcoord int not null,
	ycoord int not null,
	clue varchar(100) not null,
	direction varchar(10) not null,
	primary key (cwid, xcoord, ycoord, direction),
	index (cwid),
	foreign key (cwid)
		references Crosswords(cwid)
		on update cascade
		on delete cascade,
	foreign key (wid)
		references Words(wid)
		on update cascade
		on delete cascade
);

