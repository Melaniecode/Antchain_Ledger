from antchain_sdk_blockchain import models
from antchain_sdk_blockchain.client import Client 

# 配置設定
config = models.Config(
    access_key_id="your_access_key_id",
    access_key_secret="your_access_key_secret",
    endpoint="https://melanie9528.stkcpu.cc",
)
    
# 初始化 Client
client = Client(config)
# 存證上鏈

# 查存證

# 關閉 Client


request_data = {
    'account_id': 'user_account', 
    'mapping_info': {
        'chain_id': 'chain_id_value', 
        'other_info': 'value'
    }
}

try:
    response = client.start_account_mapping(request_data)
    print(response)
except Exception as e:
    print(f"An error occurred: {e}")
