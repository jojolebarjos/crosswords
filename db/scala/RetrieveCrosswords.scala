/**
 * Created by utkusirin on 4/2/2015.
 */
object RetrieveCrosswords extends App {
  import java.sql.{Connection, DriverManager, ResultSet};

  // create the connection
  val dbc_wodb = "jdbc:mysql://localhost:3306/CrosswordDB?user=root&password=Root2015"
  var conn = DriverManager.getConnection(dbc_wodb)
  var statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)

  // query the crossword
  statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
  try {
    // retrieve the words
    var rs = statement.executeQuery("select * from words")

    // Iterate Over ResultSet
    println("what we have in the words table:")
    while (rs.next) {
      println("wid:%d word:%s".format(rs.getInt("wid"), rs.getString("word")))
    }
    println("---------------------------------------")

    // retrieve the crosswords
    rs = statement.executeQuery("select * from crosswords")

    // Iterate Over ResultSet
    println("what we have in the crosswords table:")
    while (rs.next) {
      println("cwid:%d wid:%d xcoord:%d ycoord:%d clue:%s direction:%s".format(rs.getInt("cwid"),rs.getInt("wid"),rs.getInt("xcoord"),rs.getInt("ycoord"),rs.getString("clue"),rs.getString("direction")))
    }
    println("---------------------------------------")

    // retrieve the crosswordattributes
    rs = statement.executeQuery("select * from crosswordattributes")

    // Iterate Over ResultSet
    println("what we have in the crosswordattributes table:")
    while (rs.next) {
      println("cwid:%d source:%s language:%s title:%s url:%s author:%s cwdate:%s difficulty:%d".format(rs.getInt("cwid"),rs.getString("source"),rs.getString("language"),rs.getString("title"),rs.getString("url"),rs.getString("author"),rs.getDate("cwdate"),rs.getInt("difficulty")))
    }
    println("---------------------------------------")

    // retrieve the categories
    rs = statement.executeQuery("select * from categories")

    // Iterate Over ResultSet
    println("what we have in the categories table:")
    while (rs.next) {
      println("cid:%d category:%s".format(rs.getInt("cid"),rs.getString("category")))
    }
    println("---------------------------------------")

    // retrieve the crosswordcategories
    rs = statement.executeQuery("select * from crosswordcategories")

    // Iterate Over ResultSet
    println("what we have in the crosswordcategories table:")
    while (rs.next) {
      println("cwid:%d cid:%d".format(rs.getInt("cwid"),rs.getInt("cid")))
    }
    println("---------------------------------------")

    // retrieve the query of word3 and word4 from the adjacency matrix
    rs = statement.executeQuery("""select word, temp.sweight from
                                  |	(select crossworddb.adjacencymatrix.widfrom as candwid, sum(weight) as sweight from crossworddb.adjacencymatrix, crossworddb.words
                                  |			where (crossworddb.words.word = "come" or crossworddb.words.word = "justforadjacency") and
                                  |					crossworddb.words.wid = crossworddb.adjacencymatrix.widto
                                  |			group by crossworddb.adjacencymatrix.widfrom
                                  |	) as temp, crossworddb.words
                                  |	where words.wid = temp.candwid
                                  |    order by sweight desc""".stripMargin)

    // Iterate Over ResultSet
    println("what we have in the adjacencymatrix table:")

    while (rs.next) {
      val we = rs.getDouble("sweight");
      val wo = rs.getString("word")
      println("word:%s weight:%f".format(wo,we));
    }
    println("---------------------------------------")

  }
  finally {
    conn.close
  }

}
