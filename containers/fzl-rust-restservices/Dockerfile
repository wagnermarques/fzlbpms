# Stage 1: Build the application
FROM rust:1.76 as builder

# Create a new empty shell project
WORKDIR /usr/src/fzl-rust-restservices
COPY . .

# Build for release
RUN cargo build --release

# Stage 2: Create the final, smaller image
FROM debian:buster-slim

# Copy the built binary from the builder stage
COPY --from=builder /usr/src/fzl-rust-restservices/target/release/fzl-rust-restservices /usr/local/bin/fzl-rust-restservices

# Expose port 8080
EXPOSE 8080

# Command to run the application
CMD ["fzl-rust-restservices"]
