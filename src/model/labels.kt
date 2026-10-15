package model


fun categoryName(c: HandCategory): String = when (c) {
    HandCategory.HIGH_CARD -> "Carta alta"
    HandCategory.ONE_PAIR -> "Par"
    HandCategory.TWO_PAIR -> "Doble par"
    HandCategory.THREE_OF_A_KIND -> "Trío"
    HandCategory.STRAIGHT -> "Escalera"
    HandCategory.FLUSH -> "Color (flush)"
    HandCategory.FULL_HOUSE -> "Full house"
    HandCategory.FOUR_OF_A_KIND -> "Póker (four of a kind)"
    HandCategory.STRAIGHT_FLUSH -> "Escalera de color"
    HandCategory.ROYAL_FLUSH -> "Escalera real (royal flush)"
}

fun cardText(card: Card): String {
    val rank = when (card.rank) {
        Rank.JACK -> "J"
        Rank.QUEEN -> "Q"
        Rank.KING -> "K"
        Rank.ACE -> "A"
        else -> card.rank.value.toString()
    }
    val suit = when (card.suit) {
        Suit.CLUBS -> "C"
        Suit.DIAMONDS -> "D"
        Suit.HEARTS -> "H"
        Suit.SPADES -> "S"
    }
    return rank + suit
}

fun scoreText(s: ScoreBreakdown): String =
    "${categoryName(s.category)}: (${s.baseChips} + ${s.cardChips}) x ${s.multiplier} = ${s.total}"
