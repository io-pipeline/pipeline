#!/bin/bash

# Encrypt the .env file using OpenSSL
echo "OPENSEARCH_INITIAL_ADMIN_PASSWORD=p1p31in3eyeoh" | openssl enc -aes-256-cbc -base64 -out .env.enc

echo "Encrypted .env.enc created. To use:"
echo "openssl enc -aes-256-cbc -d -base64 -in .env.enc -out .env && docker-compose up"