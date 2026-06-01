use rand::seq::SliceRandom;

const ADJECTIVES: &[&str] = &[
    "swift", "calm", "bright", "bold", "wild", "quiet", "cool", "warm", "sharp", "smooth",
    "quick", "slow", "loud", "soft", "hard", "easy", "brave", "proud", "pure", "wise",
    "grand", "vibrant", "ancient", "modern", "hidden", "frozen", "salty", "sweet", "bitter", "sour",
    "green", "blue", "red", "gold", "silver", "iron", "stone", "water", "fire", "air",
    "small", "huge", "tiny", "tall", "short", "wide", "deep", "high", "low", "flat"
];

const NOUNS: &[&str] = &[
    "fox", "wolf", "bear", "eagle", "hawk", "lion", "tiger", "deer", "owl", "seal",
    "river", "ocean", "lake", "stream", "mountain", "hill", "valley", "plain", "forest", "desert",
    "sun", "moon", "star", "cloud", "rain", "snow", "wind", "storm", "leaf", "tree",
    "bolt", "spark", "flame", "wave", "rock", "sand", "dust", "gem", "pearl", "jade",
    "coder", "maker", "pilot", "scout", "guard", "king", "queen", "sage", "monk", "hero"
];

pub fn generate_random_subdomain() -> String {
    let mut rng = rand::thread_rng();
    let adj = ADJECTIVES.choose(&mut rng).unwrap_or(&"cool");
    let noun = NOUNS.choose(&mut rng).unwrap_or(&"host");
    let num = rand::random::<u16>() % 90 + 10; // 10-99
    format!("{}-{}-{}", adj, noun, num)
}
