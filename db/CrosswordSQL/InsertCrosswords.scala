/**
 * Created by utkusirin on 4/2/2015.
 */
object InsertCrosswords extends App {
  import java.sql.{Connection, DriverManager, ResultSet};

  // create the connection
  val dbc_wodb = "jdbc:mysql://localhost:3306/CrosswordDB?user=root&password=Root2015"
  var conn = DriverManager.getConnection(dbc_wodb)
  var statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)

  // insert a sample crossword

  // insert the words: rock, head, come
  var prep = conn.prepareStatement("insert into Words (wid, word) values (?, ?)")

  prep.setInt(1, 1)
  prep.setString(2, "rock")
  prep.executeUpdate

  prep.setInt(1, 2)
  prep.setString(2, "head")
  prep.executeUpdate

  prep.setInt(1, 3)
  prep.setString(2, "come")
  prep.executeUpdate

  // insert the attributes of the crossword:
  prep = conn.prepareStatement("insert into CrosswordAttributes (cwid,source,language,title,url,author,cwdate,difficulty) values " +
    "(?, ?, ?, ?, ?, ?, ?, ?)")

  prep.setInt(1, 1)
  prep.setString(2, "source")
  prep.setString(3, "eng")
  prep.setString(4, "tiny little")
  prep.setString(5, "no-url")
  prep.setString(6, "utku")
  prep.setDate(7, java.sql.Date.valueOf("2015-2-4"))
  prep.setInt(8, 0)
  prep.executeUpdate

  // insert the crossword
  prep = conn.prepareStatement("insert into Crosswords  (cwid,wid,xcoord,ycoord,clue,direction) values " +
    "(?, ?, ?, ?, ?, ?)")

  prep.setInt(1, 1)
  prep.setInt(2, 1)
  prep.setInt(3, 0)
  prep.setInt(4, 1)
  prep.setString(5, "Something like stone, like a big stone.")
  prep.setString(6, "South")
  prep.executeUpdate

  prep.setInt(1, 1)
  prep.setInt(2, 2)
  prep.setInt(3, 0)
  prep.setInt(4, 3)
  prep.setString(5, "Above your shoulders.")
  prep.setString(6, "South")
  prep.executeUpdate

  prep.setInt(1, 1)
  prep.setInt(2, 3)
  prep.setInt(3, 1)
  prep.setInt(4, 0)
  prep.setString(5, "Don't go, please ... back.")
  prep.setString(6, "East")
  prep.executeUpdate

  // insert the Categories: cryiptic, cat2, cat3
  prep = conn.prepareStatement("insert into Categories (cid, category) values (?, ?)")

  prep.setInt(1, 1)
  prep.setString(2, "cryriptic")
  prep.executeUpdate

  prep.setInt(1, 2)
  prep.setString(2, "category2")
  prep.executeUpdate

  prep.setInt(1, 3)
  prep.setString(2, "category3")
  prep.executeUpdate

  // insert the CrosswordCategories, assume cwid=1 has cryptic and catetory3 categories
  prep = conn.prepareStatement("insert into CrosswordCategories (cwid, cid) values (?, ?)")

  prep.setInt(1, 1)
  prep.setInt(2, 1)
  prep.executeUpdate

  prep.setInt(1, 1)
  prep.setInt(2, 3)
  prep.executeUpdate

  // close the connection
  conn.close

}
