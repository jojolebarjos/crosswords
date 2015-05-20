package crosswords.spark

import java.io.File
import java.util.NoSuchElementException
import org.apache.hadoop.io.compress.BZip2Codec
import net.didion.jwnl.data.{Synset, POS}
import org.apache.spark.{SparkContext, SparkConf}
import org.apache.spark.SparkContext._

/**
 * This object computes the wordnet path similarity between the words contained in the adjacency matrix.
 *
 * @author Matteo
 */
object WordnetSimilarity {

  // LOCAL
//  val wn_file_properties_path = "/home/matteo/Scuola/Big_Data/Project/WordNet-3.0/JWNL/jwnl14-rc2/config/file_properties.xml"
//  val dict_path = "/home/matteo/Scuola/Big_Data/Project/crosswords/data/matrix/index2word.csv.bz2"
//  val adj_matrix_path = "/home/matteo/Scuola/Big_Data/Project/crosswords/data/matrix/adjacency.csv.bz2"

  // DFS
//  val wn_file_properties_path = "hdfs:///projects/crosswords/wordnet/file_properties.xml"
  val wn_file_properties_local_path = "/home/mfilippo/file_properties.xml"
  val dict_path = "hdfs:///projects/crosswords/res/simpleNew/index2word/part*"
  val adj_matrix_path = "hdfs:///projects/crosswords/res/simpleNew/adjacency/part*"
  val output_path = "hdfs:///projects/crosswords/wordnet/wn_adjacency"


  def main(args: Array[String]) {

    val conf = new SparkConf()
      .setMaster("local")
      .setAppName("WNSim")
//      .set("spark.driver.maxResultSize", "20g")

    val sc = new SparkContext(conf)

    println("start load dictionary")
    val dict = load_dictionary(sc).collectAsMap // Question: best way to find word given id ?

    println("start load matrix")
    val adj_matrix = load_adj_matrix(sc)

    println("start mapping")
    val dict_bd = sc.broadcast(dict)
    val adj_matrix_mapped = adj_matrix.mapPartitions(it => {
      val wn = new Wordnet(new File(wn_file_properties_local_path))
      val dict = dict_bd.value
      it.map{t =>
        val w1 = dict.get(t._1).get
        val w2 = dict.get(t._2).get
        (t._1, t._2, compute_similarity(w1, w2, wn))
      }
    }, true)

    println("start writing")
    adj_matrix_mapped.saveAsTextFile(output_path, classOf[BZip2Codec])

    sc.stop()
  }

  def compute_similarity(w1: String, w2: String, wn: Wordnet): Double = {
    val synsets_w1 = wn.synsets(w1)
    val synsets_w2 = wn.synsets(w2)
    val common_pos = get_common_pos(synsets_w1, synsets_w2)
    if (common_pos.isEmpty) {
      return 0.0
    } else {
      val pos = common_pos.head match {
        case "noun" => POS.NOUN
        case "verb" => POS.VERB
        case "adjective" => POS.ADJECTIVE
        case "adverb" => POS.ADVERB
        case _ => return 0.0
      }
      val synset1 = wn.synset(w1, pos, 1)
      val synset2 = wn.synset(w2, pos, 1)
      var sim = 0.0
      try {
        sim = wn.pathSimilarity(synset1, synset2)
//        sim = wn.wupSimilarity(synset1, synset2)
//        sim = wn.lchSimilarity(synset1, synset2)
      } catch {
        case nsee: NoSuchElementException => {
          //TODO: could fix this, for now return 0.0
        }
      }
      return sim
    }
  }

  def get_common_pos(l1: List[Synset], l2: List[Synset]): Set[String] = {
    val separators = Array(' ', ']')
    val l1_pos = l1.map(s => s.getPOS.toString.split(separators)(1)).toSet
    val l2_pos = l2.map(s => s.getPOS.toString.split(separators)(1)).toSet
    return l1_pos.intersect(l2_pos)
  }

  def load_dictionary(context: SparkContext) = {
    def parse_dict_entry(s: String): (Long, String) = {
      val args = s.split(",")
      if (args(0) == "wid") (-1, args(1))
      else (args(0).toLong, args(1))
    }
    val dict = context.textFile(dict_path).map(parse_dict_entry)
    dict
  }

  def load_adj_matrix(context: SparkContext) = {
    def parse_matrix_entry(s: String): (Long, Long, Float) = {
      val args = s.split(",")
      if (args(0) == "widfrom") (-1, -1, 0)
      else (args(0).toLong, args(1).toLong, args(2).toFloat)
    }
    val matrix = context.textFile(adj_matrix_path).map(parse_matrix_entry)
    matrix
  }
}
