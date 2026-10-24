package com.example.pocketquestbudgeting.data

enum class WorthCategory(val label: String) {
    FOOD("Food"),
    ENTERTAINMENT("Entertainment"),
    TECHNOLOGY("Technology"),
    TRAVEL("Travel"),
    VEHICLES("Vehicles"),
    CUSTOM("Custom"),
}

data class WorthItem(
    val id: String,
    val name: String,
    val pluralName: String,
    // I kept estimated prices in cents, matching expenses.
    val priceCents: Long,
    val category: WorthCategory,
) {
    fun label(count: Long): String = if (count == 1L) name else pluralName
}

data class WorthComparison(val item: WorthItem, val count: Long, val remainderCents: Long)

object WorthCatalog {
    const val PRICE_DISCLAIMER = "Prices are estimates and may vary by store, location and time."

    const val COFFEE = "coffee"
    const val BURGER = "burger"
    const val MOVIE_TICKET = "movie_ticket"
    const val PHONE = "phone"
    const val WEEKEND_TRIP = "weekend_trip"
    const val CAR = "car"

    val items: List<WorthItem> = listOf(
        WorthItem(COFFEE, "coffee", "coffees", 40_00, WorthCategory.FOOD),
        WorthItem(BURGER, "burger", "burgers", 80_00, WorthCategory.FOOD),
        WorthItem("pizza", "pizza", "pizzas", 150_00, WorthCategory.FOOD),
        WorthItem("streaming_month", "month of streaming", "months of streaming", 100_00, WorthCategory.ENTERTAINMENT),
        WorthItem(MOVIE_TICKET, "movie ticket", "movie tickets", 175_00, WorthCategory.ENTERTAINMENT),
        WorthItem("concert_ticket", "concert ticket", "concert tickets", 600_00, WorthCategory.ENTERTAINMENT),
        WorthItem("earbuds", "pair of earbuds", "pairs of earbuds", 1_500_00, WorthCategory.TECHNOLOGY),
        WorthItem(PHONE, "new phone", "new phones", 8_000_00, WorthCategory.TECHNOLOGY),
        WorthItem("laptop", "laptop", "laptops", 12_000_00, WorthCategory.TECHNOLOGY),
        WorthItem("bus_trip", "long-distance bus trip", "long-distance bus trips", 300_00, WorthCategory.TRAVEL),
        WorthItem("flight", "domestic flight", "domestic flights", 2_500_00, WorthCategory.TRAVEL),
        WorthItem(WEEKEND_TRIP, "weekend trip", "weekend trips", 5_000_00, WorthCategory.TRAVEL),
        WorthItem("fuel_tank", "tank of fuel", "tanks of fuel", 1_200_00, WorthCategory.VEHICLES),
        WorthItem("car_service", "car service", "car services", 3_500_00, WorthCategory.VEHICLES),
        WorthItem(CAR, "car", "cars", 200_000_00, WorthCategory.VEHICLES),
    )

    fun item(id: String): WorthItem = items.first { it.id == id }

    fun forCategory(category: WorthCategory): List<WorthItem> = items.filter { it.category == category }

    fun custom(entity: CustomWorthItemEntity): WorthItem =
        WorthItem("custom-${entity.id}", entity.name, entity.name, entity.valueCents, WorthCategory.CUSTOM)

    // I used whole-cent division so a comparison is never rounded up to an item not yet saved.
    fun compare(savedCents: Long, item: WorthItem): WorthComparison {
        require(item.priceCents > 0) { "Enter a value greater than zero." }
        val saved = maxOf(0L, savedCents)
        return WorthComparison(item, saved / item.priceCents, saved % item.priceCents)
    }
}
