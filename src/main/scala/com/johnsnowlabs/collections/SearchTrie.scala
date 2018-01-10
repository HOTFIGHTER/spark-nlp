package com.johnsnowlabs.collections
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


/**
  * Immutable Collection that used for fast substring search
  * Implementation of Aho-Corasick algorithm https://en.wikipedia.org/wiki/Aho%E2%80%93Corasick_algorithm
  */
case class SearchTrie
(
  vocabulary: Map[String, Int],
  edges: Map[(Int, Int), Int],

  // In order to optimize 4 values are stored in the same Vector
  // Pi - prefix function
  // Is Leaf - whether node is leaf?
  // Length - length from Root to node (in words)
  // Previous Leaf - Link to leaf that suffix of current path from root
  nodes: Vector[(Int, Boolean, Int, Int)]
)
{
  /**
    * Searchs phrases in the text
    * @param text test to search in
    * @return Iterator with pairs of (begin, end)
    */
  def search(text: Seq[String]): Seq[(Int, Int)] = {
    var nodeId = 0
    val result = new ArrayBuffer[(Int, Int)]()

    def addResultIfNeed(nodeId: Int, index: Int): Unit = {
      var currentId = nodeId

      while (currentId >= 0) {
        if (isLeaf(currentId))
          result.append((index - length(currentId) + 1, index))

        currentId = lastLeaf(currentId)
      }
    }

    for ((word, index) <- text.zipWithIndex) {
      val wordId = vocabulary.getOrElse(word, -1)
      if (wordId < 0) {
        nodeId = 0
      } else {
        var found = false

        while (nodeId > 0 && !found) {
          val newId = edges.getOrElse((nodeId, wordId), -1)
          if (newId < 0) {
            nodeId = pi(nodeId)
          }
          else {
            nodeId = newId
            addResultIfNeed(nodeId, index)
            found = true
          }
        }

        if (!found) {
          nodeId = edges.getOrElse((nodeId, wordId), 0)
          addResultIfNeed(nodeId, index)
        }
      }
    }

    result
  }

  def pi(nodeId: Int): Int = nodes(nodeId)._1

  def isLeaf(nodeId: Int): Boolean = nodes(nodeId)._2

  def length(nodeId: Int): Int = nodes(nodeId)._3

  def lastLeaf(nodeId: Int): Int = nodes(nodeId)._4
}


object SearchTrie {
  def apply(phrases: Array[Array[String]]): SearchTrie = {

    // Have only root at the beginning
    val vocab = mutable.Map[String, Int]()
    val edges = mutable.Map[(Int, Int), Int]()
    val parents = mutable.ArrayBuffer(0)
    val parentWord = mutable.ArrayBuffer(0)

    val isLeaf = mutable.ArrayBuffer(false)
    val length = mutable.ArrayBuffer(0)

    def addNode(parentNodeId: Int, wordId: Int): Int = {
      parents.append(parentNodeId)
      parentWord.append(wordId)
      length.append(length(parentNodeId) + 1)
      isLeaf.append(false)

      parents.length - 1
    }

    // Add every phrase as root from root in the tree
    for (phrase <- phrases) {
      var nodeId = 0

      for (word <- phrase) {
        val wordId = vocab.getOrElseUpdate(word, vocab.size)
        nodeId = edges.getOrElseUpdate((nodeId, wordId), addNode(nodeId, wordId))
      }

      if (nodeId > 0)
        isLeaf(nodeId) = true
    }

    // Calculate pi function
    val pi = mutable.ArrayBuffer[Int](0)
    for (i <- 1 until parents.size) {
      val wordId = parentWord(i)
      var candidate = parents(i)
      pi.append(0)

      while (candidate > 0) {
        candidate = pi(candidate)
        val answer = edges.getOrElse((candidate, wordId), 0)
        if (answer > 0) {
          pi(i) = answer
          candidate = 0
        }
      }
    }

    val lastLeaf = ArrayBuffer[Int](-1)
    for (i <- 1 until parents.size) {
      lastLeaf.append(-1)

      val piNode = pi(i)
      if (isLeaf(piNode))
        lastLeaf(i) = piNode
      else
        lastLeaf(i) = lastLeaf(piNode)
    }

    val nodes = pi.zip(isLeaf).zip(length).zip(lastLeaf)
      .map{case (((a,b),c),d) => (a,b,c,d)}.toVector

    SearchTrie(vocab.toMap, edges.toMap, nodes)
  }
}