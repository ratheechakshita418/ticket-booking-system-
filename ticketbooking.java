// Concurrent Ticket Booking System with Redis Locking

const express = require("express");
const redis = require("redis");
const { v4: uuidv4 } = require("uuid");

const app = express();
app.use(express.json());

// Redis Client
const redisClient = redis.createClient({
    url: "redis://127.0.0.1:6379"
});

redisClient.on("error", (err) => console.log("Redis Error:", err));

(async () => {
    await redisClient.connect();
    console.log("Redis Connected");
})();

// In-memory seat storage
const seats = {};
const TOTAL_SEATS = 20;

// Initialize seats
for (let i = 1; i <= TOTAL_SEATS; i++) {
    seats[i] = {
        seatId: i,
        booked: false
    };
}

// Lock timeout (5 seconds)
const LOCK_TIMEOUT = 5000;

// Home route
app.get("/", (req, res) => {
    res.send("Concurrent Ticket Booking System Running");
});

// View all seats
app.get("/seats", (req, res) => {
    res.json(seats);
});

// Book seat with Redis Lock
app.post("/book/:id", async (req, res) => {

    const seatId = req.params.id;
    const lockKey = `lock:seat:${seatId}`;
    const lockValue = uuidv4();

    try {

        // Try acquiring lock
        const lock = await redisClient.set(lockKey, lockValue, {
            NX: true,
            PX: LOCK_TIMEOUT
        });

        if (!lock) {
            return res.json({
                success: false,
                message: "Seat is currently locked by another user"
            });
        }

        // Check seat
        if (!seats[seatId]) {
            return res.json({
                success: false,
                message: "Seat not found"
            });
        }

        if (seats[seatId].booked) {
            return res.json({
                success: false,
                message: "Seat already booked"
            });
        }

        // Book seat
        seats[seatId].booked = true;

        // Release lock
        await redisClient.del(lockKey);

        res.json({
            success: true,
            message: `Seat ${seatId} booked successfully`
        });

    } catch (error) {

        await redisClient.del(lockKey);

        res.json({
            success: false,
            error: error.message
        });
    }

});

// Start server
const PORT = 3000;

app.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
});
