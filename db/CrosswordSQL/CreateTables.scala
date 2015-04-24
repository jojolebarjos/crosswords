/**
 * Created by utkusirin on 4/2/2015.
 */
object CreateTables extends App {
  import java.sql.{Connection, DriverManager, ResultSet};

  // create the connection
  val dbc_wodb = "jdbc:mysql://localhost:3306/?user=root&password=Root2015"
  var conn = DriverManager.getConnection(dbc_wodb)
  var statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)

  // drop the database if exists
  var prep = conn.prepareStatement("drop database if exists CrosswordDB")
  prep.executeUpdate

  // create the database
  try {
    val prep = conn.prepareStatement("create database CrosswordDB")
    prep.executeUpdate
  }
  finally {
    conn.close
  }

  // create the tables in the database
  val dbc = "jdbc:mysql://localhost:3306/CrosswordDB?user=root&password=Root2015" // observe that we specify the database name this time
  conn = DriverManager.getConnection(dbc)
  statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)

  // create CrosswordAttributes table
  prep = conn.prepareStatement("create table CrosswordAttributes (cwid int," +
                                                                  "source varchar(50)," +
                                                                  "language varchar(20)," +
                                                                  "title varchar(50)," +
                                                                  "url varchar(100)," +
                                                                  "author varchar(30)," +
                                                                  "cwdate date," +
                                                                  "difficulty int," +
                                                                  "primary key (cwid))")
  prep.executeUpdate

  // create Words table
  prep = conn.prepareStatement("create table Words (wid int," +
                                                    "word varchar(50)," +
                                                    "primary key (wid))")
  prep.executeUpdate

  // create Crosswords table
  prep = conn.prepareStatement("create table Crosswords ( cwid int," +
                                                          "wid int," +
                                                          "xcoord int," +
                                                          "ycoord int," +
                                                          "clue varchar(100)," +
                                                          "direction varchar(10)," +
                                                          "primary key (cwid,wid)," +
                                                          "foreign key (cwid) " +
                                                                "references CrosswordAttributes(cwid) " +
                                                                "on update cascade on delete cascade," +
                                                          "foreign key (wid) " +
                                                                "references Words(wid) " +
                                                                "on update cascade on delete cascade)")
  prep.executeUpdate

  // create Categories table
  prep = conn.prepareStatement("create table Categories ( cid int," +
                                                          "category varchar(50)," +
                                                          "primary key (cid))")
  prep.executeUpdate

  // create CrosswordCategory table
  prep = conn.prepareStatement("create table CrosswordCategories (  cwid int," +
                                                                    "cid int," +
                                                                    "primary key (cwid,cid)," +
                                                                    "foreign key (cwid) " +
                                                                          "references CrosswordAttributes(cwid) " +
                                                                          "on update cascade on delete cascade," +
                                                                    "foreign key (cid) " +
                                                                          "references Categories(cid) " +
                                                                          "on update cascade on delete cascade)")
  prep.executeUpdate

  // create AdjacencyMatrix table
  prep = conn.prepareStatement("create table AdjacencyMatrix (  widfrom int," +
                                                                "widto int," +
                                                                "weight double," +
                                                                "primary key (widfrom,widto)," +
                                                                "foreign key (widfrom) " +
                                                                    "references Words(wid) " +
                                                                    "on update cascade on delete cascade," +
                                                                "foreign key (widto) " +
                                                                    "references Words(wid) " +
                                                                    "on update cascade on delete cascade)")
  prep.executeUpdate

  // close the connection
  conn.close

}
