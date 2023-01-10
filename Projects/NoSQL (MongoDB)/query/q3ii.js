// Task 3ii

db.credits.aggregate([
    {$unwind: "$crew"},
    {
        $match: 
        {$and: 
            [
                {"crew.department": "Directing"},
                {"crew.job": "Director"},
                {"crew.id": 5655},
            ]
        }
    
    },
    {$unwind: "$cast"},
    {$project:
        {
            _id: "$cast.id",
            name:"$cast.name"
        }
    },
    {$group: 
        {
            _id: {val1: "$_id", val2: "$name"},
            count: {$sum: 1}
        },
    },
    {$project:
        {
            _id: 0,
            count: 1,
            id: "$_id.val1", 
            name: "$_id.val2"
        }
    },
    {$sort: {count: -1, id: 1}},
    {$limit: 5}

]);