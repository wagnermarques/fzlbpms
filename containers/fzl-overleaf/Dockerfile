FROM overleaf/sharelatex:latest

# Switch to the root user to gain permissions for installation
USER root

# Update the package lists and install pandoc
# We also clean up the apt cache in the same layer to keep the image size down
RUN apt-get update && \
    apt-get install -y pandoc --no-install-recommends && \
    rm -rf /var/lib/apt/lists/*

# Switch back to the default, non-root user for better security
USER sharelatex
