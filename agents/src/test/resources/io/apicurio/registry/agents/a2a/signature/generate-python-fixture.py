import json
from cryptography.hazmat.primitives.asymmetric import ec, ed25519
from jwt.api_jwk import PyJWK
from jwt.algorithms import ECAlgorithm, OKPAlgorithm
from google.protobuf.json_format import ParseDict, MessageToDict
from a2a.types import AgentCard
from a2a.utils.signing import create_agent_card_signer, create_signature_verifier

card_json = {
  "name": "Interop Agent \u00e9\u20ac",
  "description": "Signed by the a2a-python SDK",
  "supportedInterfaces": [{"url": "https://example.com/a2a/v1", "protocolBinding": "JSONRPC", "protocolVersion": "1.0"}],
  "version": "1.0.0",
  "capabilities": {"streaming": False, "pushNotifications": True, "extensions": []},
  "defaultInputModes": ["text/plain"],
  "defaultOutputModes": ["text/plain"],
  "skills": [{"id": "echo", "name": "Echo", "description": "Echoes the input.", "tags": ["echo", "t\u00e9st"]}]
}
ec_key = ec.generate_private_key(ec.SECP256R1())
ed_key = ed25519.Ed25519PrivateKey.generate()
card = ParseDict(card_json, AgentCard())
card = create_agent_card_signer(ec_key, {"alg": "ES256", "typ": "JOSE", "kid": "py-es256"})(card)
card = create_agent_card_signer(ed_key, {"alg": "EdDSA", "typ": "JOSE", "kid": "py-eddsa"})(card)
sigs = MessageToDict(card)["signatures"]
out = dict(card_json); out["signatures"] = sigs
jwk_ec = json.loads(ECAlgorithm.to_jwk(ec_key.public_key())); jwk_ec.update(kid="py-es256", use="sig", alg="ES256")
jwk_ed = json.loads(OKPAlgorithm.to_jwk(ed_key.public_key())); jwk_ed.update(kid="py-eddsa", use="sig", alg="EdDSA")
print(json.dumps(MessageToDict(card)["capabilities"]))
json.dump(out, open("python-signed-card.json","w"), indent=2, ensure_ascii=False)
json.dump({"keys":[jwk_ec, jwk_ed]}, open("python-jwks.json","w"), indent=2)
# self-check
def kp(kid, jku):
    return PyJWK(jwk_ec if kid=="py-es256" else jwk_ed)
create_signature_verifier(kp, ["ES256","EdDSA"])(card); print("py verify ok")
