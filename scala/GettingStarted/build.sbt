import sbt.Keys.javacOptions

lazy val root = (project in file(".")).
  settings(
    name := "getting-started-scala",
    version := "1.0",
    scalaVersion := "3.3.0",
    mainClass := Some("com.amazonaws.services.kinesisanalytics.main"),
    javacOptions ++= Seq("-source", "11", "-target", "11")
  )

val jarName = "ScalaGettingStarted-flink_1.19.jar"
val flinkVersion = "1.19.1"
val msfRuntimeVersion = "1.2.0"
val connectorVersion = "4.3.0-1.19"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-kinesisanalytics-runtime" % msfRuntimeVersion,
  "org.apache.flink" % "flink-connector-kinesis" % connectorVersion,
  "org.apache.flink" % "flink-streaming-java" % flinkVersion % "provided",
  "org.apache.flink" % "flink-connector-base" % flinkVersion % "provided",
)

artifactName := { (_: ScalaVersion, _: ModuleID, _: Artifact) => jarName }

assembly / assemblyJarName := jarName
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}