import os

patterns = [
    ('getName()', 'getTitle()'),
    ('setName(', 'setTitle('),
    ('getTypeId()', 'getCategoryId()'),
    ('setTypeId(', 'setCategoryId('),
    ('getArea()', 'getCampus()'),
    ('setArea(', 'setCampus('),
    ('getAddress()', 'getLocation()'),
    ('setAddress(', 'setLocation('),
    ('getAvgPrice()', 'getPrice()'),
    ('setAvgPrice(', 'setPrice('),
    ('getShopId()', 'getRelatedId()'),
    ('setShopId(', 'setRelatedId(')
]

def process_file(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
            
        new_content = content
        for old, new in patterns:
            new_content = new_content.replace(old, new)
            
        if new_content != content:
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(new_content)
    except Exception as e:
        print(f"Error processing {filepath}: {e}")

for root, dirs, files in os.walk('src'):
    for file in files:
        if file.endswith('.java'):
            process_file(os.path.join(root, file))

print("Method Refactoring complete.")
