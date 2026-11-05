package com.example.recipegen

import kotlin.math.roundToInt

/** Turns the classifier's dish predictions into ingredients (+ full steps when the recipe book has the dish). */
object IngredientBook {
    // Food-101 label -> recipe in RecipeBook (only where we have a full recipe)
    private val recipeFor = mapOf(
        "pizza" to "Margherita Pizza", "hamburger" to "Classic Cheeseburger",
        "spaghetti_bolognese" to "Spaghetti with Tomato Sauce", "spaghetti_carbonara" to "Spaghetti with Tomato Sauce",
        "caesar_salad" to "Caesar Salad", "greek_salad" to "Fresh Garden Salad", "sushi" to "Salmon Sushi Rolls",
        "fried_rice" to "Egg Fried Rice", "pancakes" to "Fluffy Pancakes", "chocolate_cake" to "Chocolate Cake",
        "tacos" to "Beef Tacos", "omelette" to "Cheese Omelette", "steak" to "Grilled Steak",
        "french_fries" to "Crispy Oven Fries", "grilled_cheese_sandwich" to "Grilled Cheese Sandwich",
        "guacamole" to "Guacamole", "hummus" to "Hummus", "lasagna" to "Lasagna",
        "macaroni_and_cheese" to "Mac and Cheese", "pad_thai" to "Pad Thai", "garlic_bread" to "Garlic Bread",
        "ice_cream" to "Vanilla Ice Cream (no churn)", "samosa" to "Punjabi Samosa",
        "chicken_curry" to "Butter Chicken (Murgh Makhani)",
    )

    fun pretty(label: String) = label.replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    fun toRecipes(preds: List<Pair<String, Float>>): List<Recipe> = preds.map { (label, conf) ->
        val name = pretty(label)
        val book = findRecipe(label)
        val ingredients = book?.ingredients ?: ingredientTable[label].orEmpty()
        Recipe(
            title = "$name — ${(conf * 100).roundToInt()}%",
            keywords = emptyList(),
            ingredients = ingredients.ifEmpty { listOf("No ingredient list stored for this dish yet.") },
            steps = book?.steps ?: listOf("No step-by-step recipe stored for this dish yet."),
        )
    }

    private fun findRecipe(label: String): Recipe? {
        recipeFor[label]?.let { t -> RecipeBook.all.firstOrNull { it.title == t }?.let { return it } }
        // For custom-trained classes (e.g. Indian dishes): exact keyword or title match.
        val n = label.replace('_', ' ').lowercase()
        return RecipeBook.all.firstOrNull { r -> r.keywords.any { it == n } || (n.length >= 4 && r.title.lowercase().contains(n)) }
    }

    private val ingredientTable: Map<String, List<String>> by lazy {
        RAW.trim().lines().associate { line ->
            val (k, v) = line.split("|"); k.trim() to v.split(",").map { it.trim() }
        }
    }

    private const val RAW = """
apple_pie|Apples,Pastry dough,Sugar,Cinnamon,Butter,Flour
baby_back_ribs|Pork ribs,BBQ sauce,Brown sugar,Paprika,Garlic powder,Salt
baklava|Filo pastry,Walnuts or pistachios,Butter,Honey,Sugar,Cinnamon
beef_carpaccio|Raw beef fillet,Olive oil,Lemon,Parmesan,Rocket,Capers
beef_tartare|Raw beef,Egg yolk,Capers,Shallot,Dijon mustard,Worcestershire sauce
beet_salad|Beetroot,Goat cheese,Walnuts,Rocket,Olive oil,Balsamic vinegar
beignets|Flour,Yeast,Milk,Eggs,Sugar,Icing sugar,Oil
bibimbap|Rice,Beef,Spinach,Carrot,Bean sprouts,Egg,Gochujang,Sesame oil
bread_pudding|Stale bread,Milk,Eggs,Sugar,Raisins,Cinnamon,Butter
breakfast_burrito|Flour tortilla,Scrambled eggs,Bacon or sausage,Cheese,Potatoes,Salsa
bruschetta|Baguette,Tomatoes,Garlic,Basil,Olive oil,Balsamic vinegar
caesar_salad|Romaine lettuce,Croutons,Parmesan,Egg yolk,Anchovies,Lemon,Garlic
cannoli|Pastry shells,Ricotta,Sugar,Chocolate chips,Vanilla,Pistachios
caprese_salad|Tomatoes,Fresh mozzarella,Basil,Olive oil,Balsamic glaze,Salt
carrot_cake|Carrots,Flour,Sugar,Eggs,Oil,Walnuts,Cinnamon,Cream cheese frosting
ceviche|Raw white fish,Lime juice,Red onion,Coriander,Chilli,Tomato
cheese_plate|Assorted cheeses,Crackers,Grapes,Nuts,Honey,Cured meats
cheesecake|Cream cheese,Digestive biscuits,Butter,Sugar,Eggs,Vanilla,Sour cream
chicken_curry|Chicken,Onion,Tomato,Ginger-garlic paste,Curry powder,Coconut milk or yogurt
chicken_quesadilla|Flour tortilla,Chicken,Cheese,Bell pepper,Onion,Salsa
chicken_wings|Chicken wings,Hot sauce,Butter,Garlic powder,Paprika,Salt
chocolate_cake|Flour,Cocoa,Sugar,Eggs,Milk,Oil,Baking powder
chocolate_mousse|Dark chocolate,Eggs,Cream,Sugar,Vanilla
churros|Flour,Water,Butter,Eggs,Sugar,Cinnamon,Oil
clam_chowder|Clams,Potatoes,Bacon,Onion,Celery,Cream,Milk
club_sandwich|Toast,Turkey,Bacon,Lettuce,Tomato,Mayonnaise
crab_cakes|Crab meat,Breadcrumbs,Egg,Mayonnaise,Mustard,Old Bay seasoning
creme_brulee|Cream,Egg yolks,Sugar,Vanilla
croque_madame|Bread,Ham,Gruyère,Béchamel,Fried egg,Butter
cup_cakes|Flour,Sugar,Butter,Eggs,Milk,Vanilla,Frosting
deviled_eggs|Hard-boiled eggs,Mayonnaise,Dijon mustard,Paprika,Vinegar
donuts|Flour,Yeast,Milk,Sugar,Eggs,Butter,Oil,Glaze
dumplings|Dumpling wrappers,Minced pork or vegetables,Ginger,Garlic,Soy sauce,Spring onion
edamame|Young soybeans in pods,Sea salt
eggs_benedict|English muffin,Poached eggs,Ham,Hollandaise sauce
escargots|Snails,Butter,Garlic,Parsley,Shallots
falafel|Chickpeas,Onion,Garlic,Parsley,Coriander,Cumin,Flour,Oil
filet_mignon|Beef tenderloin,Butter,Garlic,Thyme,Salt,Pepper
fish_and_chips|White fish,Flour,Beer or sparkling water,Potatoes,Oil,Salt
foie_gras|Duck or goose liver,Salt,Pepper,Brioche
french_fries|Potatoes,Oil,Salt
french_onion_soup|Onions,Beef stock,Butter,Baguette,Gruyère,Thyme
french_toast|Bread,Eggs,Milk,Cinnamon,Butter,Maple syrup
fried_calamari|Squid,Flour,Egg,Oil,Lemon,Paprika
fried_rice|Cooked rice,Eggs,Peas and carrots,Spring onion,Soy sauce,Oil
frozen_yogurt|Yogurt,Sugar,Vanilla,Fruit
garlic_bread|Baguette,Butter,Garlic,Parsley
gnocchi|Potatoes,Flour,Egg,Salt,Butter or sauce
greek_salad|Tomatoes,Cucumber,Red onion,Feta,Olives,Olive oil,Oregano
grilled_cheese_sandwich|Bread,Cheddar,Butter
grilled_salmon|Salmon fillet,Olive oil,Lemon,Garlic,Dill,Salt
guacamole|Avocados,Lime,Onion,Tomato,Coriander,Jalapeño
gyoza|Gyoza wrappers,Minced pork,Cabbage,Garlic,Ginger,Soy sauce
hamburger|Ground beef,Burger bun,Lettuce,Tomato,Onion,Cheese,Pickles
hot_and_sour_soup|Mushrooms,Tofu,Bamboo shoots,Egg,Vinegar,White pepper,Chicken stock
hot_dog|Sausage,Hot dog bun,Mustard,Ketchup,Onion
huevos_rancheros|Fried eggs,Corn tortillas,Black beans,Tomato salsa,Cheese
hummus|Chickpeas,Tahini,Lemon,Garlic,Olive oil
ice_cream|Cream,Milk,Sugar,Egg yolks,Vanilla
lasagna|Lasagna sheets,Ground beef,Tomato sauce,Béchamel,Mozzarella,Parmesan
lobster_bisque|Lobster,Cream,Tomato paste,Brandy,Onion,Celery,Butter
lobster_roll_sandwich|Lobster meat,Mayonnaise,Celery,Lemon,Buttered roll
macaroni_and_cheese|Macaroni,Cheddar,Milk,Butter,Flour
macarons|Almond flour,Icing sugar,Egg whites,Sugar,Buttercream filling
miso_soup|Miso paste,Dashi,Tofu,Wakame,Spring onion
mussels|Mussels,White wine,Garlic,Shallots,Parsley,Butter
nachos|Tortilla chips,Cheese,Jalapeños,Beans,Salsa,Sour cream
omelette|Eggs,Butter,Cheese,Salt,Pepper
onion_rings|Onions,Flour,Egg,Breadcrumbs,Oil,Paprika
oysters|Fresh oysters,Lemon,Mignonette sauce
pad_thai|Rice noodles,Shrimp or tofu,Egg,Bean sprouts,Tamarind,Fish sauce,Peanuts
paella|Rice,Saffron,Chicken,Seafood,Peppers,Peas,Olive oil,Stock
pancakes|Flour,Milk,Eggs,Baking powder,Sugar,Butter
panna_cotta|Cream,Sugar,Gelatin,Vanilla
peking_duck|Duck,Honey,Five-spice,Pancakes,Spring onion,Hoisin sauce,Cucumber
pho|Rice noodles,Beef,Beef broth,Star anise,Ginger,Onion,Herbs,Lime
pizza|Pizza dough,Tomato sauce,Mozzarella,Basil,Olive oil
pork_chop|Pork chops,Garlic,Thyme,Butter,Salt,Pepper
poutine|French fries,Cheese curds,Gravy
prime_rib|Beef rib roast,Garlic,Rosemary,Salt,Pepper,Butter
pulled_pork_sandwich|Pork shoulder,BBQ sauce,Brown sugar,Paprika,Burger bun,Coleslaw
ramen|Ramen noodles,Pork or chicken broth,Egg,Pork belly,Spring onion,Nori
ravioli|Pasta dough,Ricotta,Spinach,Parmesan,Tomato sauce
red_velvet_cake|Flour,Cocoa,Buttermilk,Red food colouring,Eggs,Cream cheese frosting
risotto|Arborio rice,Stock,Onion,White wine,Parmesan,Butter
samosa|Flour,Potatoes,Peas,Cumin,Coriander powder,Garam masala,Oil
sashimi|Raw fish (salmon or tuna),Soy sauce,Wasabi,Pickled ginger
scallops|Sea scallops,Butter,Garlic,Lemon,Parsley
seaweed_salad|Wakame seaweed,Sesame oil,Rice vinegar,Sesame seeds,Soy sauce
shrimp_and_grits|Shrimp,Grits,Bacon,Garlic,Butter,Cheese
spaghetti_bolognese|Spaghetti,Ground beef,Tomatoes,Onion,Carrot,Garlic,Parmesan
spaghetti_carbonara|Spaghetti,Eggs,Pecorino,Guanciale or pancetta,Black pepper
spring_rolls|Rice paper or wrappers,Vegetables,Vermicelli,Prawns or tofu,Herbs,Dipping sauce
steak|Steak,Salt,Pepper,Oil,Butter,Garlic,Thyme
strawberry_shortcake|Strawberries,Flour,Butter,Sugar,Cream,Milk
sushi|Sushi rice,Rice vinegar,Nori,Raw fish,Cucumber,Soy sauce,Wasabi
tacos|Tortillas,Ground beef or chicken,Lettuce,Tomato,Cheese,Salsa
takoyaki|Flour,Dashi,Eggs,Octopus,Spring onion,Takoyaki sauce,Bonito flakes
tiramisu|Ladyfingers,Mascarpone,Eggs,Coffee,Sugar,Cocoa
tuna_tartare|Raw tuna,Avocado,Soy sauce,Sesame oil,Lime,Spring onion
waffles|Flour,Eggs,Milk,Butter,Baking powder,Sugar,Vanilla
"""
}
