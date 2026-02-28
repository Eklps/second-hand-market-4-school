import os

patterns = [
    ('ShopType', 'Category'),
    ('shopType', 'category'),
    ('tb_shop_type', 'tb_category'),
    ('BlogComments', 'PostComments'),
    ('blogComments', 'postComments'),
    ('tb_blog_comments', 'tb_post_comments'),
    ('Shop', 'Product'),
    ('shop', 'product'),
    ('tb_shop', 'tb_product'),
    ('Blog', 'Post'),
    ('blog', 'post'),
    ('tb_blog', 'tb_post')
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
        if file.endswith('.java') or file.endswith('.xml') or file.endswith('.yml') or file.endswith('.yaml'):
            process_file(os.path.join(root, file))

print("Refactoring complete.")
