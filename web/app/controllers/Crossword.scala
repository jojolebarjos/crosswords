package controllers

import scala.collection.mutable
import play.api.libs.json.{Json, JsValue, JsArray}

/**
 * Represents a crossword
 * @param json a json representation of a crossword
 */
class Crossword(val json: JsValue) {
    
    val title = (json \ "title").asOpt[String]
    val source = (json \ "source").asOpt[String]
    val author = (json \ "author").asOpt[String]
    val url = (json \ "url").asOpt[String]
    
    val words = (json \ "words").asInstanceOf[JsArray].value.map(w => (
        Crossword.clean((w \ "word").as[String]),
        (w \ "clue").as[String],
        Vec((w \ "x").as[Int], (w \ "y").as[Int]),
        Direction((w \ "dir").as[String])
    ))
    
    require(!words.exists(w => w._4 == West || w._4 == North), "West and North words are not supported")
    
    val width = words.map(w => w._3.x + (if (w._4 == East) w._1.size - 1 else 0)).max + 1
    val height = words.map(w => w._3.y + (if (w._4 == South) w._1.size - 1 else 0)).max + 1
    
    val coordinates = (0 until height).flatMap(y => (0 until width).map(x => Vec(x, y)))
    
    val locations = coordinates.filter(v => words.exists(_._3 == v)).zipWithIndex.map(p => (p._1, p._2 + 1))
    
    val chars = {
        val map = new mutable.HashMap[Vec, Char]()
        for ((w, _, v, d) <- words; i <- 0 until w.size) {
            val c = w(i)
            val p = v + Vec(d) * i
            if (map.contains(p))
                require(map(p) == c, "characters at same location must match")
            map(p) = c
        }
        map.toMap.withDefaultValue(' ')
    }
    
    val indices = locations.toMap.withDefaultValue(0)
    
    val grid = coordinates.map(v => v -> (chars(v), indices(v))).toMap
    
    val across = locations.flatMap(l => words.find(w => w._4 == East && w._3 == l._1).map(w => (l._2, w._2)))
    val down = locations.flatMap(l => words.find(w => w._4 == South && w._3 == l._1).map(w => (l._2, w._2)))
    
}

/**
 * Represents a crossword
 */
object Crossword {
    
    def clean(word: String) =
      word.toUpperCase.replace("[^A-Z]", "")
    
}
