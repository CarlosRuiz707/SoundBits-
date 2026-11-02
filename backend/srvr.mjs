import express from 'express';
import cors from 'cors';
import YTMusic from 'ytmusic-api';

const app = express();
app.use(cors());

const ytmusic = new YTMusic();
await ytmusic.initialize();

// Buscar canciones
app.get('/search', async (req, res) => {
  try {
    const query = req.query.q || 'ne deve ne kush';
    const songs = await ytmusic.search(query, 'song');
    res.json(songs);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Obtener álbum
app.get('/album/:id', async (req, res) => {
  try {
    const album = await ytmusic.getAlbum(req.params.id);
    res.json(album);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Obtener artista
app.get('/artist/:id', async (req, res) => {
  try {
    const artist = await ytmusic.getArtist(req.params.id);
    res.json(artist);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Servidor corriendo en puerto ${PORT}`));