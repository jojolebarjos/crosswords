/**
 * Created by utkusirin on 3/18/2015.
 */
object MySQLHelloWorld extends App {
  import java.sql.{Connection, DriverManager, ResultSet};

  // 0) drop the database if already exists
  val dbc_wodb = "jdbc:mysql://localhost:3306/?user=root&password=Root2015"
  var conn = DriverManager.getConnection(dbc_wodb)
  var statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)
  // drop the database
  var prep = conn.prepareStatement("drop database if exists testDatabase")
  prep.executeUpdate

  // 1) create the database
  // create the database
  try {
    val prep = conn.prepareStatement("create database testDatabase")
    prep.executeUpdate
  }
  finally {
    conn.close
  }

  // 2) create a table in the database
  val dbc = "jdbc:mysql://localhost:3306/testDatabase?user=root&password=Root2015" // observe that we specify the database name this time
  conn = DriverManager.getConnection(dbc)
  statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)
  // create the table

  prep = conn.prepareStatement("create table testTable (id int, val int)")
  prep.executeUpdate


  // 3) insert three tuples to the database
  // since we already connected to the database we will use the same database connection with same statement configuration
  // insert the tuples: (6,12) (7,14) (8,16)

  prep = conn.prepareStatement("insert into testTable (id, val) values (?, ?)")

  prep.setInt(1, 6)
  prep.setInt(2, 12)
  prep.executeUpdate

  prep.setInt(1, 7)
  prep.setInt(2, 14)
  prep.executeUpdate

  prep.setInt(1, 8)
  prep.setInt(2, 16)
  prep.executeUpdate

  // 4) select two tuples from the table
  // observe we change the statement configuration to read only since this is a select statment
  // but we keep using the same database connection since the database we connected is the same
  statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
  try {
    // select the tuples (7,14) and (8,16)
    val rs = statement.executeQuery("select * from testTable where id > 6")

    // Iterate Over ResultSet
    while (rs.next) {
      println("id:%d val:%d".format(rs.getInt("id"), rs.getInt("val")))
    }
  }
  finally {
    conn.close
  }
}
