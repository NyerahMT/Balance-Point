from pathlib import Path

path = Path('core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java')
text = path.read_text()
old = '''        Model dashScreenM = b.createRect(\n                -0.060f, 0f, -0.024f,\n                -0.060f, 0f,  0.029f,\n                 0.060f, 0f,  0.029f,\n                 0.060f, 0f, -0.024f,\n                 0f, 1f, 0f, dashScreenMat,\n                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal\n                        | VertexAttributes.Usage.TextureCoordinates);'''
new = '''        // UV orientation matters here: createRect maps its first edge to texture U.\n        // Run that edge across the handlebars (X), not fore/aft (Z), so the LCD\n        // texture is physically landscape on the dashboard instead of rotated 90 deg.\n        Model dashScreenM = b.createRect(\n                -0.060f, 0f, -0.024f,\n                 0.060f, 0f, -0.024f,\n                 0.060f, 0f,  0.029f,\n                -0.060f, 0f,  0.029f,\n                 0f, 1f, 0f, dashScreenMat,\n                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal\n                        | VertexAttributes.Usage.TextureCoordinates);'''
if old not in text:
    raise SystemExit('dashboard screen rectangle block not found; refusing blind patch')
path.write_text(text.replace(old, new, 1))
