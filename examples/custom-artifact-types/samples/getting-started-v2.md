# Getting started with the Orders API

## Authentication

Obtain a token from the identity provider and pass it as a bearer token.

## Creating an order

`POST /orders` with the order payload. The response contains the generated order id.

## Cancelling an order

`DELETE /orders/{id}` cancels an order that has not been shipped yet.
