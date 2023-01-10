// Task 2ii

db.movies_metadata.aggregate([
    // TODO: Write your query here
    {$project:{_id: {$split: ["$tagline", " "]}}},
    {$unwind: "$_id"},
    {$project:{_id: {$toLower: {$trim: {input: "$_id", chars: ".,?!" }}}}},
    {
        $group: {
            _id: "$_id", 
            count: {$sum: 1}, 
        }
     },
     {$project:{count: 1, len : {$strLenCP: "$_id"}}},
     {$match: {len: {$gte: 4}}},
     {$sort: {count: -1}},
     {$limit: 20},
     {$project:{len: 0}}
]);