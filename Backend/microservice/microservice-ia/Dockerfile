# ─────────────────────────────────────────
# STAGE 1 : Build Java avec Maven
# ─────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# ─────────────────────────────────────────
# STAGE 2 : Image finale Java + Python
# ─────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Installer Python + outils de compilation
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python3-venv \
    python3-dev \
    gcc \
    g++ \
    make \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Copier le jar Java buildé
COPY --from=build /app/target/*.jar app.jar

# Copier tout le dossier Python
COPY src/main/Python ./Python

# Créer un environnement virtuel et installer les dépendances
RUN python3 -m venv /app/venv
RUN /app/venv/bin/pip install --no-cache-dir \
    numpy \
    pandas \
    joblib \
    scikit-learn \
    prophet

ENV IA_MODELS_DIR=/app/Python/models/
ENV PYTHONPATH=/app/Python
ENV PATH="/app/venv/bin:$PATH"

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]