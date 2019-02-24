module.exports = {
    logSingleMeal: (meal) => {
        console.log('RECEIVING SINGLE MEAL: ');
        console.log(meal);
    },
    logBatchMeal: (batchMeals) => {
        console.log('RECEIVING BATCH OF MEALS: ');
        console.log(batchMeals.meals);
    },
    logInitiation: () => console.log('\n========================\nBEGINNING DEMO!\n========================\n'),
    logCompletion: () => console.log('\n========================\nDEMO COMPLETE!\n========================\n'),
}