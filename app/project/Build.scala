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
    val newDependencies = dependencies.map(module => if (module.name.contains("spark")) module % "provided" else module)
    extracted.append(Seq(libraryDependencies := newDependencies), state)
  }
}
