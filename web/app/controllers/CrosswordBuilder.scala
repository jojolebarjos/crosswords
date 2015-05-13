package controllers

import java.io.{BufferedWriter, File, FileWriter}

import play.api.libs.json.{JsArray, JsString, JsValue, Json}

import scala.util.Random
import scala.util.control.Breaks._

/**
 * Build a crossword with a list of words and a list of clues
 */
object CrosswordBuilder {

  /**
   * Try to place the nextword in the board with an intersection with a word in matchedWords and with respect of words alreadyPlaced
   * @param matchedWords the words that match the nextword
   * @param nextWord the next word to place
   * @param board the crossword board
   * @param alreadyPlaced the words already placed
   * @return true if the word can be placed in the board
   */
  def tryToPlace(matchedWords: List[Word], nextWord: Word, board: Array[Array[Char]], alreadyPlaced: List[Word]): Boolean = {
    //5 step
    var placed = false
    breakable {
      for (w <- matchedWords) {
        // (index first same char in nextword, index first same char in w)
        for (t <- w.getCharsPosition.filter(c => nextWord.word.contains(c._1))) {
          for (c <- nextWord.getCharsPosition.filter(k => t._1 == k._1)) {
            val intersection = t._2
            if (w.direction == "South") {

              nextWord.setCoord(intersection._1 - c._3, intersection._2, "East")
            } else {
              nextWord.setCoord(intersection._1, intersection._2 - c._3, "South")
            }

            placed = placed || (nextWord.isConsitent(alreadyPlaced) && nextWord.placeInBoard(board, intersection))
            if (placed) {
              break
            }
          }
        }
      }
    }

    placed
  }

  /**
   * Implement an algorithm to build a crossword based on a list of words and a list of clues
   * @param words list of words
   * @param clue list of clues
   * @return a json represnetation of a crossword
   */
  // Check http://stackoverflow.com/questions/943113/algorithm-to-generate-a-crossword
  def generateCrossword(words: List[String], clue: List[String]): JsValue =  {

    val jsonTextBegin = """{
      "source" : "The CrossWord Generator",
      "language" : "eng",
      "title" : "New crossword """ + crosswordNumber + """",
      "url" : "http://wiki.epfl.ch/bigdata2015-crosswords",
      "date" : "2015-05-07",
      "author" : "The CrossWord Team",
       "words" : [ """
    val jsonTextEnd = """]}"""

    var crosswordWords: List[Word] = List()

    // Transform words and clue into Word
    var allWords : List[Word] = List()
    for (i <- 0 to words.length - 1) {
      allWords = new Word(words(i), clue(i)) :: allWords
    }


    //1 step
    val wordsSorted = allWords.sortBy(_.word.length).reverse
    var board : Array[Array[Char]] = Array.ofDim[Char](wordsSorted(0).word.length, wordsSorted(0).word.length)


    //2 step
    wordsSorted(0).setCoord(0, board.size / 2, "East")
    wordsSorted(0).placeInBoard(board, (0, board.size / 2))
    crosswordWords = wordsSorted(0) :: crosswordWords
    var remainingWords = wordsSorted

    do {
      //3 step
      remainingWords = remainingWords.tail

      //4 step
      val nextWord = remainingWords(0)


      val matchedWords = crosswordWords.filter(word =>
        word.word.map(c => nextWord.word.contains(c)).foldLeft(false)(_ || _)
      )

      if (tryToPlace(matchedWords, nextWord, board, crosswordWords)) {
        crosswordWords = nextWord :: crosswordWords
      }

    } while (!remainingWords.tail.isEmpty)


    val jsonText: String = if (crosswordWords.size == 1) { crosswordWords(0).toJson() } else {
      crosswordWords.map(_.toJson()).reduce(_ + ", " + _)
    }

    // print the result
    println(board.deep.mkString("\n"))
    println(jsonTextBegin + jsonText + jsonTextEnd)

    Json.parse(jsonTextBegin + jsonText + jsonTextEnd)
  }

  //Create random crosswords
  var crosswordNumber = 1
  val allWiktionary = Packer.read("""/home/tux/Desktop/wiktionary""")

  val allWordsDefinitions: Map[String, Option[JsArray]] = allWiktionary.flatMap(jsonFile => jsonFile.asInstanceOf[JsArray].value.map(json => {
    val word = (json \ "word").asInstanceOf[JsString]
    val definitions = (json \ "definitions").asOpt[JsArray]
    (word, definitions)
  })).map(t => (t._1.value.toUpperCase(), t._2)).toMap

  /**
   * Generate maxNumberCrossword crossword
   * @param maxNumberCrosswords the max number of crossword needed
   */
  def generateCrosswordsAndPushToTheDatabase(maxNumberCrosswords: Int) = {
    while (crosswordNumber <= maxNumberCrosswords) {
      val wordsList = Search.getRandomWordsFromDB(10000).filter(t => (t._2.size >= 2) && (t._2.size <= 17))
      val wordsWithDefinitions = wordsList.filter(w => if (allWordsDefinitions.contains(w._2)) {
        allWordsDefinitions(w._2) match {
          case Some(jsArray) =>
            !jsArray.value.filter(_.asInstanceOf[JsString].value.length < 100).isEmpty
          case None =>
            false
        }
      } else {
        false
      })

      // (Index word, word, definition)
      val wordsDefinition: List[(Int, String, String)] = wordsWithDefinitions.map(t => {
        (t._1, t._2, allWordsDefinitions(t._2) match {
          case Some(jsArray) => {
            val rand = new Random(System.currentTimeMillis())
            val random_index = rand.nextInt(jsArray.value.length)
            (jsArray.value(random_index)).asInstanceOf[JsString].value
          }
          case None =>
            ""
        })
      })


      val wordIndexMap = wordsDefinition.map(t => (t._2, t._1)).toMap
      val crossword = new Crossword(generateCrossword(wordsDefinition.map(_._2), wordsDefinition.map(_._3.replaceAll("""[^A-Za-z0-9 ]""", " "))))

      insertCrosswordIntoDB(crossword, wordIndexMap)
      crosswordNumber += 1
    }
  }

  val output: File = new File("output.txt")
  val outputStream = new BufferedWriter(new FileWriter(output))
  /**
   * Insert a crossword into the database with respect of words index
   * @param crossword the crossword
   * @param wordIndex the words indexes
   */
  def insertCrosswordIntoDB(crossword: Crossword, wordIndex: Map[String, Int]) = {

    /*
    val dbc = "jdbc:sqlite:../db/cw"//"jdbc:mysql://192.168.56.1:3306/testDatabase?user=root&password=vm" // observe that we specify the database name this time
    var conn = DriverManager.getConnection(dbc)
    var statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)
    // create the table

    var prep = conn.prepareStatement("INSERT INTO crosswords (cwid,source,lang,title,url,author,cwdate,difficulty) " +
      """VALUES ('""" + crosswordNumber + """','""" + crossword.source.get + """','eng','""" +
      crossword.title.get + """','""" + crossword.url.get +
    """','""" + crossword.author.get + """','2015/05/07',1);""")

    prep.executeUpdate

    for (wordTuple <- crossword.words) {

      prep = conn.prepareStatement("INSERT INTO items (cwid,wid,xcoord,ycoord,clue,direction) " +
        "VALUES (" + crosswordNumber + "," + wordIndex(wordTuple._1) + "," + wordTuple._3.x + "," + wordTuple._3.y +
        """,'""" + wordTuple._2 + """','""" + wordTuple._4 + """');""")

      prep.executeUpdate
    }

    */

    val i1 = "INSERT INTO crosswords (cwid,source,lang,title,url,author,cwdate,difficulty) " +
      """VALUES ('""" + crosswordNumber + """','""" + crossword.source.get + """','eng','""" +
      crossword.title.get + """','""" + crossword.url.get +
      """','""" + crossword.author.get + """','2015/05/07',1);"""

    outputStream.write(i1)

    for (wordTuple <- crossword.words) {

      var i2 = "INSERT INTO items (cwid,wid,xcoord,ycoord,clue,direction) " +
        "VALUES (" + crosswordNumber + "," + wordIndex(wordTuple._1) + "," + wordTuple._3.x + "," + wordTuple._3.y +
        """,'""" + wordTuple._2 + """','""" + wordTuple._4 + """');"""

      outputStream.write(i2)
    }
  }


  def main(args: Array[String]) {
    //generateCrossword(wordsTest, definitionsTest)
    generateCrosswordsAndPushToTheDatabase(1000)
    outputStream.close()
  }
}

/**
 * Represent a word in the CrosswordBuilder context
 * @param word the word
 * @param clue the clue
 */
class Word(val word: String, val clue: String) {
  var xcoord: Int = 0
  var ycoord: Int = 0
  var direction: String = ""

  /**
   * Convert the word into json format
   * @return a string representation of the json
   */
  def toJson(): String =
    """{ "word" : """" + word + """", "clue" : """" + clue + """", "x" : """ + xcoord + """, "y" : """ + ycoord + """, "dir" : """" + direction + """" }"""

  /**
   * Set the new position and diretion of the word
   * @param x the x coordinate
   * @param y the y coordinate
   * @param dir the direction
   */
  def setCoord(x: Int, y: Int, dir: String): Unit = {
    xcoord = x
    ycoord = y
    direction = dir
  }

  /**
   * Get the coordinate of the i char
   * @param i the index of the char of the word
   * @return
   */
  def getCharCoord(i: Int): (Int, Int) = {
    if (direction == "South") {
      (xcoord, ycoord + i)
    } else {
      (xcoord + i, ycoord)
    }
  }

  /**
   * Check if the word is not in conflict with the words already placed
   * @param alreadyPlaced the words already placed
   * @return true if the word could be placed at its position
   */
  def isConsitent(alreadyPlaced: List[Word]): Boolean = {
    var allPosition: List[(Int, Int)] = List()
    var badPosition: List[(Int, Int)] = List()
    for (i <- 0 to word.size) {
      if (direction == "South") {
        allPosition = (xcoord, ycoord + i) :: allPosition
      } else {
        allPosition = (xcoord + i, ycoord) :: allPosition
      }
    }
    if (direction == "South") {
      badPosition = (xcoord, ycoord - 1) :: badPosition
      badPosition = (xcoord, ycoord + word.length) :: badPosition
    } else {
      badPosition = (xcoord - 1, ycoord) :: badPosition
      badPosition = (xcoord + word.length, ycoord) :: badPosition
    }

    val containsWord = !(alreadyPlaced.map(placedWord =>
      (placedWord.direction == direction) && allPosition.contains((placedWord.xcoord, placedWord.ycoord))
    ).foldLeft(false)(_ || _))

    val boundaryCases = !alreadyPlaced.map(placedWord => {
      var positionsChars: List[(Int, Int)] = List()
      for (w <- 0 to placedWord.word.size - 1) {
        if (placedWord.direction == "South") {
          positionsChars = (placedWord.xcoord, placedWord.ycoord + w) :: positionsChars
        } else {
          positionsChars = (placedWord.xcoord + w, placedWord.ycoord) :: positionsChars
        }
      }

      if (placedWord.direction == "South") {
        positionsChars = (placedWord.xcoord, placedWord.ycoord + placedWord.word.size) :: positionsChars
        positionsChars = (placedWord.xcoord, placedWord.ycoord - 1) :: positionsChars
      } else {
        positionsChars = (placedWord.xcoord + placedWord.word.size, placedWord.ycoord) :: positionsChars
        positionsChars = (placedWord.xcoord - 1, placedWord.ycoord) :: positionsChars
      }

      (positionsChars.map(pos =>
        badPosition.contains(pos)
      ).foldLeft(false)(_ || _))
    }
    ).foldLeft(false)(_ || _)

    containsWord && boundaryCases
  }

  /**
   * Get the position of all chars in the word
   * @return a lst of (char, position)
   */
  def getCharsPosition = {
    var res: List[(Char, (Int, Int), Int)] = List()

    for (i <- 0 to word.length - 1) {
      if (direction == "South") {
        res = (word(i), (xcoord, ycoord + i), i) :: res
      } else {
        res = (word(i), (xcoord + i, ycoord), i) :: res
      }
    }

    res
  }

  /**
   * Place the word in the board at the specific intersection with additionnal check
   * @param board the board
   * @param intersection the intersection point
   * @return true if the word can be placed
   */
  def placeInBoard(board: Array[Array[Char]], intersection: (Int, Int)): Boolean = {
    var placed = true
    if (((direction == "South") && ((ycoord + word.size) > board(0).size))
      || ((direction == "East") && ((xcoord + word.size) > board.size))
    || (xcoord < 0) || (ycoord < 0)) {
      placed = false
    } else {


      if (direction == "South") {
        for (i <- 0 to word.size - 1) {
          if (((board(xcoord)(ycoord + i).isLetter) && (board(xcoord)(ycoord + i) != word(i)))
          || (((xcoord + 1) < board.size) && ((ycoord + i) != intersection._2) && ((board(xcoord + 1)(ycoord + i).isLetter)))
            || (((xcoord - 1) >= 0) && ((ycoord + i) != intersection._2) && ((board(xcoord - 1)(ycoord + i).isLetter)))) {
            placed = false
          }
        }

        if(placed) {
          for (i <- 0 to word.size - 1) {
            board(xcoord)(ycoord + i) = word(i)
          }
        }
      } else {
        for (i <- 0 to word.size - 1) {
          if (((board(xcoord + i)(ycoord).isLetter) && (board(xcoord + i)(ycoord) != word(i)))
            || (((ycoord + 1) < board(0).size) && ((xcoord + i) != intersection._1) && ((board(xcoord + i)(ycoord + 1).isLetter)))
            || (((ycoord - 1) >= 0) && ((xcoord + i) != intersection._1) && ((board(xcoord + i)(ycoord - 1).isLetter)))) {
            placed = false
          }
        }
        if (placed) {
          for (i <- 0 to word.size - 1) {
            board(xcoord + i)(ycoord) = word(i)
          }
        }
      }
    }

    placed
  }
}