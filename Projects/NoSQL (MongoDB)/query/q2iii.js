// Task 2iii

db.movies_metadata.aggregate([
    {$project: 
        {_id: 
            {$cond: 
                {if: 
                    {$or: 
                        [
                            {$eq: ["$budget", false]},
                            {$eq: ["$budget", null]}, 
                            {$eq: ["$budget", ""]}, 
                            {$eq: ["$budget", undefined]}
                        ]
                    }, 
                then: "unknown", 
                else: "$budget"}}
        }
    },
    {$project: 
        {_id: 
            {$cond: 
                {if:
                    {$or: 
                        [
                            {$isNumber: "$_id"},
                            {$eq: ["$_id", "unknown"]} 
                        ]
                    },  
                then: "$_id", 
                else: {$toInt: {$trim: {input: "$_id", chars: "USD$ " }}}}}
        }
    },
    {$project: 
        {_id: 
            {$cond: 
                {if:{$isNumber: "$_id"},
                then: {$round: ["$_id", -7]}, 
                else: "$_id"}
            }
        }
    },
    {
        $group: {
            _id: "$_id", // Group by the field movieId
            count: {$sum: 1} // Get the count for each group
        }
     },
     {$project:
        {
            _id: 0,
            budget: "$_id",
            count: 1
        }
    },
    {$sort: {budget: 1}}


]);