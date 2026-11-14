# syntax=docker.io/docker/dockerfile:1

# Base image for Jib (see build.gradle), which can't add operating system packages itself: ImageMagick makes the
# variants of uploaded images (WebP and JPEG in three sizes) and reads HEIC through libheif; the fonts are for the
# captions of the generated sample images.
# CI publishes it as bloomper/fgv-spexipedia-server-base:25 before each image build, so ImageMagick gets security updates.
FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends imagemagick libmagickcore-7.q16-10-extra libheif1 libheif-plugin-libde265 webp fonts-dejavu-core fontconfig \
    && rm -rf /var/lib/apt/lists/*
# Uploads are untrusted: only the coders Spexipedia uses, no delegates, and resource limits (see policy.xml)
COPY policy.xml /etc/ImageMagick-7/policy.xml
