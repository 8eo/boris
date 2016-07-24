
name := "boris"
version := "0.0.1"
scalaVersion := "2.11.8"
fork in Test := true
scalafmtConfig := Some(file(".scalafmt"))
scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-Ywarn-dead-code",
  "-encoding", "UTF-8",
  "-target:jvm-1.8",
  "-feature",
  "-language:postfixOps")

libraryDependencies ++= {
  val akkaV        = "2.4.8"
  val scalaTestV   = "2.2.6"
  Seq(
    "com.typesafe.akka"   %% "akka-actor"                         % akkaV,
    "com.typesafe.akka"   %% "akka-stream"                        % akkaV,
    "com.typesafe.akka"   %% "akka-http-core"                     % akkaV,
    "com.typesafe.akka"   %% "akka-http-experimental"             % akkaV,
    "com.typesafe.akka"   %% "akka-http-spray-json-experimental"  % akkaV,
    "com.typesafe.akka"   %% "akka-http-testkit"                  % akkaV,
    "org.scalatest"       %% "scalatest"                          % scalaTestV    % "test"
  )
}
