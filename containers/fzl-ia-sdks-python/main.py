from agents import Agent, ModelSettings
from agents import Runner

# Define a simple agent
agent = Agent(
    name="Travel Genie",
    instructions=(
        "You are Travel Genie, a friendly and knowledgeable travel assistant. "
        "Recommend exciting destinations and offer helpful travel tips."
    ),
    
    model="gpt-4.1",
    
    model_settings = ModelSettings(
        temperature=0.7,  # Controls creativity (0-2)
        max_tokens=500    # Limits response length
    )  # Specify which model to use
)

result = Runner.run_sync(
    starting_agent=agent,
    input="What's your top recommendation for adventure seekers in Brazil, Sao Paulo, to climb moutains?"
)

print(result.final_output)

