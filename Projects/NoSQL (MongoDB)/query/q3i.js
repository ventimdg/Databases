// Task 3i

db.credits.aggregate([
    // TODO: Write your query here
    {
        $match: {cast: { $elemMatch: { name: "Stan Lee" } } }
    
    },

    {
        $lookup: {
            from: "movies_metadata", // Search inside movies_metadata
            localField: "movieId", // match our _id
            foreignField: "movieId", // with the "movieId" in movies_metadata
            as: "movies" // Put matching rows into the field "movies"
        }
    },

    {$unwind: "$cast"},

    {
        $match: {"cast.name": "Stan Lee"  } 
    
    },

    {$project:
        {
            _id: 0,
            title: {$first: "$movies.title"},
            release_date: {$first: "$movies.release_date"},
            character: "$cast.character"
        }
    },
    {$sort: {release_date: -1}}

]);