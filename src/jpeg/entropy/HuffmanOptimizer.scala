package jpeg

import scala.collection.mutable

/** Builds JPEG-compatible, length-limited canonical Huffman tables from symbol frequencies.
  *
  * JPEG forbids an all-one code and limits codes to 16 bits. A pseudo-symbol prevents the first
  * problem; the redistribution procedure from T.81 Annex K.2 constrains the generated tree.
  */
object HuffmanOptimizer:
  private val PseudoSymbol = 256

  /** Optimizes a non-empty alphabet whose symbols are unsigned bytes. */
  def optimize(frequencies: Map[Int, Long]): HuffmanTable =
    require(frequencies.nonEmpty, "at least one Huffman symbol is required")
    require(frequencies.keys.forall(symbol => symbol >= 0 && symbol <= 255))
    require(frequencies.values.forall(_ > 0), "Huffman frequencies must be positive")

    val depths = buildDepths(frequencies.updated(PseudoSymbol, 1L))
    val bits   = Array.fill(33)(0)
    depths.values.foreach(depth => bits(math.min(depth, 32)) += 1)
    limitCodeLengths(bits)

    // Remove the pseudo-symbol from the longest occupied code length. Because it sorts last among
    // equal-frequency leaves, this is equivalent to removing its canonical code.
    val longest = (32 to 1 by -1).find(bits(_) > 0).get
    bits(longest) -= 1

    val orderedSymbols = frequencies.keys.toSeq.sortBy(symbol => (depths(symbol), symbol))
    HuffmanTable(bits.slice(1, 17).toSeq, orderedSymbols)

  private def buildDepths(frequencies: Map[Int, Long]): Map[Int, Int] =
    given Ordering[Node] = Ordering.by[Node, (Long, Int)](node => (-node.weight, -node.minimum))
    val queue = mutable.PriorityQueue.from(
      frequencies.map((symbol, weight) => Node(weight, symbol, Some(symbol), None, None))
    )
    while queue.size > 1 do
      val left  = queue.dequeue()
      val right = queue.dequeue()
      queue.enqueue(Node(
        left.weight + right.weight,
        math.min(left.minimum, right.minimum),
        None,
        Some(left),
        Some(right)
      ))
    val result = mutable.Map.empty[Int, Int]
    def visit(node: Node, depth: Int): Unit = node.symbol match
      case Some(symbol) => result(symbol) = math.max(1, depth)
      case None         =>
        visit(node.left.get, depth + 1)
        visit(node.right.get, depth + 1)
    visit(queue.dequeue(), 0)
    result.toMap

  /** Rebalances overly deep leaves while preserving the Kraft sum (T.81 K.2, Figure K.1). */
  private def limitCodeLengths(bits: Array[Int]): Unit =
    for length <- 32 to 17 by -1 do
      while bits(length) > 0 do
        val shorter = (length - 2 to 1 by -1).find(bits(_) > 0).getOrElse(
          throw JpegError("cannot limit Huffman code lengths")
        )
        bits(length) -= 2
        bits(length - 1) += 1
        bits(shorter + 1) += 2
        bits(shorter) -= 1

  private final case class Node(
      weight: Long,
      minimum: Int,
      symbol: Option[Int],
      left: Option[Node],
      right: Option[Node]
  )

