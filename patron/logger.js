module.exports = {
    logSingleMeal: (meal) => {
        console.log("Receiving single meal: ");
        console.log(meal);
    },
    logBatchMeal: (batchMeals) => {
        console.log("Receiving batch of meals: ");
        console.log(batchMeals.meals);
        // batchMeals.meals.forEach((meal, index) => console.log("#" + (index + 1) + ": " + meal.name));
    },
    logInitiation: (command) => console.log("BEGINNING " + command.toUpperCase() + " DEMO!"),
    logCompletion: (command) => console.log(command.toUpperCase() + " DEMO COMPLETE!"),
}