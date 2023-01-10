// Task 1i

db.keywords.aggregate([
    // TODO: Write your query here
    {
        $match:
        {
            $or: 
            [
                { keywords: { $elemMatch: { name: "mickey mouse" } } },
                { keywords: { $elemMatch: { name: "marvel comic" } } }
            ]
        }
    },
    { $project: { _id: 0, keywords: 0 } },
    { $sort: { movieId: 1 } }
]);