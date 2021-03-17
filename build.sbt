name := "boris"
organization := "co.horn"
scalaVersion := "2.12.13"
//Do not remove it it's used in lambda package and it's still 2.11
//because of https://github.com/datastax/spark-cassandra-connector
crossScalaVersions := Seq("2.11.8", "2.12.13")
fork in Test := true

scalacOptions ++= Seq("-unchecked",
                      "-deprecation",
                      "-Ywarn-dead-code",
                      "-encoding",
                      "UTF-8",
                      "-target:jvm-1.8",
                      "-feature",
                      "-language:postfixOps")

// Sbt seems to have some issues with publishing packages with credentials and below line is an workaround
// for this bug: https://github.com/sbt/sbt/issues/3570
updateOptions := updateOptions.value.withGigahorse(false)

publishTo := Some("Horn SBT" at "https://sbt.horn.co/repository/internal")
credentials += Credentials(
  "Repository Archiva Managed internal Repository",
  "sbt.horn.co",
  sys.env("HORN_SBT_USERNAME"),
  sys.env("HORN_SBT_PASSWORD")
)

libraryDependencies ++= {
  val akkaHttpV = "10.2.4"
  val akkaV = "2.6.13"
  val scalaTestV = "3.0.5"
  Seq(
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test",
    "org.scalatest" %% "scalatest" % scalaTestV % "test"
  )
}

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
