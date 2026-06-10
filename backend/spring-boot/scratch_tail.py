import re
html_path = r"d:\D\RESUME PROJECTS\Cloud Pool\backend\spring-boot\src\main\resources\static\index.html"
with open(html_path, "r", encoding="utf-8") as f:
    text = f.read()

pages = list(re.finditer(r'<div class="page"[^>]*>', text))
print("Found pages:", len(pages))
if pages:
    last_page = pages[-1]
    print("Last page starts at:", last_page.start(), "Content:", last_page.group(0))
    # Write the tail of the file to investigate structure
    with open("scratch_tail.txt", "w", encoding="utf-8") as out:
        out.write(text[last_page.start():])
