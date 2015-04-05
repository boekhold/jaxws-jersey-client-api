Description
=============
This is an experiment to see if we can tweak the JAX-WS request pipeline to use a
Jersey/JAX-RS Client object for the actual HTTP(S) request.

The idea behind this is that this could provide us with a common API to set up things such as
proxy parameters, authentication, SSL/TLS etc, shared across REST calls, JAX-WS Web Service
calls, plain HTTP(S) calls.

References
==========

- [jax-ws source][]
- [smtp-tube][]

[jax-ws source]: https://jax-ws.java.net/nonav/jax-ws-20-fcs/arch/index.html?com/sun/xml/ws/api/pipe/TransportPipeFactory.html
[smtp-tube]: https://jax-ws-commons.java.net/smtp/