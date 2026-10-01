from pathlib import Path

p = Path('core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java')
s = p.read_text()

s = s.replace('''        environment.set(new ColorAttribute(ColorAttribute.Fog,\n                0.67f, 0.755f, 0.79f, 1f));\n''', '')
s = s.replace('''        drawSkyBackground();\n\n        modelBatch.begin(camera);''', '''        modelBatch.begin(camera);''')
start = s.find('    /**\n     * Lightweight atmospheric sky.')
if start != -1:
    end = s.find('    private void drawHud()', start)
    if end == -1:
        raise SystemExit('drawHud anchor not found')
    s = s[:start] + s[end:]

p.write_text(s)
