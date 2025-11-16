# BOSQICH 1: LOYIHANI YIG'ISH (BUILD)
# Maven va OpenJDK 17 bazaviy rasmini yuklash
FROM maven:3.8.6-openjdk-17 AS build
WORKDIR /app

# Pom.xml faylini nusxalash va bog'liqliklarni (dependencies) yuklash (keshdan foydalanish uchun muhim)
COPY pom.xml .
RUN mvn dependency:go-offline

# Qolgan kodni nusxalash
COPY src ./src

# Loyihani yig'ish va JAR faylini target papkasida yaratish
RUN mvn clean package -DskipTests

# BOSQICH 2: ISHGA TUSHIRISH (RUNTIME)
# Eng kichik OpenJDK 17 rasmini yuklash (hajmini kamaytirish uchun)
FROM openjdk:17-jdk-slim
WORKDIR /app

# Birinchi bosqichdan yig'ilgan JAR faylini nusxalash va uni 'app.jar' deb nomlash
# (Maven yig'ilgan fayl nomi o'rniga * ishlatish, agar nom o'zgarsa, qidirib topish uchun)
COPY --from=build /app/target/*.jar app.jar

# Botni ishga tushirish buyrug'i. 
# Bu buyruq botni 24/7 ishga tushiradi.
ENTRYPOINT ["java", "-jar", "app.jar"]