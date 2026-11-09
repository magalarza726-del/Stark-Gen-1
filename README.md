# Stark Gen 1 – AirBoard Draw

Reinicio total de AirBoard Draw. Gen 1 resuelve un único problema deliberadamente limitado:

> seguir una tapa azul sobre un fondo blanco o uniforme y convertir su movimiento en trazos digitales.

## v1.0.0

- Segmentación de azul en HSV, sin IA ni datasets.
- Componentes conectados para rechazar ruido.
- Continuidad temporal: prioriza el objeto cercano a la posición anterior.
- Calibración corta de matiz y área esperada.
- CameraX con toque para enfocar y botón **Reenfocar**.
- Pantalla de inicio, inicio principal, tutorial, calibración y dibujo.
- Herramientas mínimas: Selección, Dibujar, Borrar y Limpiar.
- Sin ARCore, espacios 3D ni reconocimiento genérico de bolígrafos.

## Restricciones de Gen 1

- Usar una tapa azul saturada.
- Preferir fondo blanco o de color uniforme.
- Evitar otros objetos azules dentro de la imagen.
- Mantener la tapa cerca del punto de escritura para reducir error geométrico.

## APK

Cada actualización en `main` compila el proyecto y publica el APK en el Release `v1.0.0` como:

`Stark-Gen1-AirBoard-Draw-v1.0.0.apk`
