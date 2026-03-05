import mysql.connector

try:
    conn = mysql.connector.connect(
        host="127.0.0.1",
        user="root",
        password="123", # wait, password is 123456
        database="secondhand"
    )
except Exception:
    conn = mysql.connector.connect(
        host="127.0.0.1",
        user="root",
        password="123456",
        database="secondhand"
    )

cursor = conn.cursor(dictionary=True)
cursor.execute("SELECT id, product_id, title FROM tb_voucher")
for row in cursor.fetchall():
    print(f"Voucher ID: {row['id']}, Product ID (which used to be shop_id): {row.get('product_id', row.get('shop_id'))}, Title: {row['title']}")

cursor.execute("SELECT * FROM tb_seckill_voucher")
print("\n--- tb_seckill_voucher ---")
for row in cursor.fetchall():
    print(row)
