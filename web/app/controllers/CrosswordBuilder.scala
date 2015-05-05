package controllers

import play.api.libs.json.{JsValue, Json}
import scala.util.control.Breaks._

object CrosswordBuilder {
  
  //Sample test
  val wordsTest = List("chocolat", "chien", "chat", "oiseau", "maison")
  val definitionsTest = List(
    "Substance alimentaire à base de cacao et de sucre.",
    "sous-espèce domestique de Canis lupus",
    "mammifère carnivore de la famille des félidés",
    "vertébrés tétrapodes ailés appartenant au clade des dinosaures",
    "Famille appartenant à la noblesse"
  )


  val jsonTextBegin = """{
      "source" : "The CrossWord User",
      "language" : "eng",
      "title" : "New crossword",
      "url" : "http://wiki.epfl.ch/bigdata2015-crosswords",
      "date" : "2015-03-10",
       "words" : [ """
  val jsonTextEnd = """]}"""

  def tryToPlace(matchedWords: List[Word], nextWord: Word, board: Array[Array[Char]]): Boolean = {
    //5 step
    var placed = false
    breakable {
      for (w <- matchedWords) {
        // (index first same char in nextword, index first same char in w)
        for (t <- w.word.map(c => Tuple2(nextWord.word.indexOf(c), w.word.indexOf(c))
        ).filter(_._1 != -1)) {
          val intersection = w.getCharCoord(t._2)
          if (w.direction == "South") {
            nextWord.setCoord(intersection._1 - t._1, intersection._2, "East")
          } else {
            nextWord.setCoord(intersection._1, intersection._2 - t._1, "South")
          }

          placed = placed || nextWord.placeInBoard(board, intersection)
          if (placed) {
            break
          }
        }
      }
    }

    placed
  }

  def main(args: Array[String]) {
    generateCrossword(wordsTest, definitionsTest)
  }

  // Check http://stackoverflow.com/questions/943113/algorithm-to-generate-a-crossword
  def generateCrossword(words: List[String], clue: List[String]): JsValue =  {
    var crosswordWords: List[Word] = List()

    // Transform words and clue into Word
    var allWords : List[Word] = List()
    for (i <- 0 to words.length - 1) {
      allWords = new Word(words(i), clue(i)) :: allWords
    }

    var wordsPlaced: List[Word] = List()


    //1 step
    val wordsSorted = allWords.sortBy(_.word.length).reverse
    var board : Array[Array[Char]] = Array.ofDim[Char](wordsSorted(0).word.length, wordsSorted(0).word.length)


    //2 step
    wordsSorted(0).setCoord(0, board.size / 2, "East")
    wordsSorted(0).placeInBoard(board, (0, board.size / 2))
    wordsPlaced = (wordsSorted(0) :: wordsPlaced).reverse
    crosswordWords = wordsSorted(0) :: crosswordWords
    var remainingWords = wordsSorted

    do {
      //3 step
      remainingWords = remainingWords.tail

      //4 step
      val nextWord = remainingWords(0)


      val matchedWords = wordsPlaced.filter(word =>
        word.word.map(c => nextWord.word.contains(c)).foldLeft(false)(_ || _)
      )

      if (tryToPlace(matchedWords, nextWord, board)) {
        crosswordWords = nextWord :: crosswordWords
      }

    } while (!remainingWords.tail.isEmpty)


    val jsonText: String = if (crosswordWords.size == 1) { crosswordWords(0).toJson() } else {
      crosswordWords.map(_.toJson()).reduce(_ + ", " + _)
    }
    println(jsonTextBegin + jsonText + jsonTextEnd)
    Json.parse(jsonTextBegin + jsonText + jsonTextEnd)
  }
}

class Word(val word: String, val clue: String) {
  var xcoord: Int = 0
  var ycoord: Int = 0
  var direction: String = ""

  def toJson(): String =
    """{ "word" : """" + word + """", "clue" : """" + clue + """", "x" : """ + xcoord + """, "y" : """ + ycoord + """, "dir" : """" + direction + """" }"""

  def setCoord(x: Int, y: Int, dir: String): Unit = {
    xcoord = x
    ycoord = y
    direction = dir
  }

  def getCharCoord(i: Int): (Int, Int) = {
    if (direction == "South") {
      (xcoord, ycoord + i)
    } else {
      (xcoord + i, ycoord)
    }
  }

  def placeInBoard(board: Array[Array[Char]], intersection: (Int, Int)): Boolean = {
    var placed = true
    if (((direction == "South") && ((ycoord + word.size) > board(0).size))
      || ((direction == "East") && ((xcoord + word.size) > board.size))
    || (xcoord < 0) || (ycoord < 0)) {
      placed = false
    } else {

      println(board.deep.mkString("\n"))
      println(word)
      println(xcoord + "; " + ycoord)

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