import sbt._
import Keys._

object Build extends Build
{
  lazy override val projects = Seq(app)
  lazy val app = Project("app", file(".")) settings(
    commands += cluster
  )

  // Use the cluster command to change the dependencies for the cluster: --> sbt cluster compile assembly <-- to create the jar
  // --> spark-submit --class package.MainClass --master yarn-cluster --num-executors 25 pathToJar.jar <-- to run on the cluster
  val cluster = Command.command("cluster") { state =>
    val extracted = Project.extract(state)
    val dependencies = extracted.get(libraryDependencies)
    val newDependencies = dependencies.flatMap(module =>
      if (module.name.contains("spark")) {
        // Force Spark 1.2.1 on the cluster
        module.organization %% module.name % "1.2.1" % "provided" :: Nil
      } else if (module.name.contains("hadoop")) {
        // No need to force a hadoop version
        Nil
      } else {
        module :: Nil
      })
    extracted.append(Seq(libraryDependencies := newDependencies), state)
  }
}
