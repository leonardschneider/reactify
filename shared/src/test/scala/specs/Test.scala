package specs

import reactify._

object Test {
  def main(args: Array[String]): Unit = {
    val s1 = Var("One")
    val s2 = Var("Two")
    val list = Var(List.empty[String])
    list := {
      println(s"Evaluating! s1: ${s1()}, s2: ${s2()}, list: ${list()}")
      s1() :: s2() :: list()
    }
    println(s"${list().mkString(", ")} should be One, Two")
    s2 := "Three"
    println(s"${list().mkString(", ")} should be One, Three")
    val states = list.instance.get()
    println(s"States? ${states.size}")
    println(s"States? ${states.head.function()} / ${states.tail.head.function()}")
  }
}
