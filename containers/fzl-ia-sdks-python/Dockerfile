FROM python:3.10-slim

WORKDIR /app

RUN pip install openai-agents

COPY . .

EXPOSE 8000

CMD ["python", "main.py"]




